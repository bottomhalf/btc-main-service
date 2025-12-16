package bt.conference.service;

import bt.conference.model.GlobalSearchResponse;
import bt.conference.model.GlobalSearchResponse.GroupedResults;
import bt.conference.model.GlobalSearchResponse.SearchMetadata;

import bt.conference.model.SearchResultItem;
import bt.conference.repository.GlobalSearchRepository;

import com.fierhub.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GlobalSearchService {

    private static final Logger logger = LoggerFactory.getLogger(GlobalSearchService.class);
    private final GlobalSearchRepository searchRepository;
    private final UserSession userSession;

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int TYPEAHEAD_LIMIT = 5; // Results per category for typeahead

    @Autowired
    public GlobalSearchService(GlobalSearchRepository searchRepository, UserSession userSession) {
        this.searchRepository = searchRepository;
        this.userSession = userSession;
    }

    public boolean isHealthy() {
        return searchRepository.isHealthy();
    }

    public Map<String, Object> getMetrics() {
        return searchRepository.getMetrics();
    }

    /**
     * Typeahead search - fast results as user types
     * Returns limited results per category (5 each)
     */
    public GlobalSearchResponse typeahead(String searchTerm, String fullSearch) {
        logger.debug("Typeahead: query='{}', user='{}' isFullSearch='{}'", searchTerm, userSession.getUserId(), fullSearch);
        long startTime = System.currentTimeMillis();

        if (!isValidSearchTerm(searchTerm)) {
            return emptyResponse(searchTerm, 0, TYPEAHEAD_LIMIT, startTime, true);
        }

        try {
            Map<String, List<SearchResultItem>> results = searchRepository.typeaheadSearch(
                    searchTerm.trim(), userSession.getUserId(), fullSearch, TYPEAHEAD_LIMIT);

            return buildResponse(results, searchTerm, 0, TYPEAHEAD_LIMIT, startTime, true, false);

        } catch (GlobalSearchException e) {
            logger.warn("Typeahead search failed: {}", e.getMessage());
            return errorResponse(e, searchTerm, startTime);
        } catch (Exception e) {
            logger.error("Unexpected typeahead error: {}", e.getMessage(), e);
            return unexpectedErrorResponse(searchTerm, startTime);
        }
    }

    /**
     * Full search with pagination
     */
    public GlobalSearchResponse search(String searchTerm, String currentUserId, int page, int limit) {
        long startTime = System.currentTimeMillis();

        if (!isValidSearchTerm(searchTerm)) {
            return emptyResponse(searchTerm, page, limit, startTime, false);
        }

        // Sanitize pagination
        page = Math.max(0, page);
        limit = Math.min(Math.max(1, limit), MAX_LIMIT);
        int skip = page * limit;

        try {
            Map<String, List<SearchResultItem>> results = searchRepository.globalSearch(
                    searchTerm.trim(), currentUserId, skip, limit);

            return buildResponse(results, searchTerm, page, limit, startTime, false, false);

        } catch (GlobalSearchException e) {
            logger.warn("Global search failed: {}", e.getMessage());
            return errorResponse(e, searchTerm, startTime);
        } catch (Exception e) {
            logger.error("Unexpected search error: {}", e.getMessage(), e);
            return unexpectedErrorResponse(searchTerm, startTime);
        }
    }

    /**
     * Search only users
     */
    public GlobalSearchResponse searchUsers(String searchTerm, String currentUserId, int page, int limit) {
        long startTime = System.currentTimeMillis();

        if (!isValidSearchTerm(searchTerm)) {
            return emptyResponse(searchTerm, page, limit, startTime, false);
        }

        page = Math.max(0, page);
        limit = Math.min(Math.max(1, limit), MAX_LIMIT);

        try {
            Map<String, List<SearchResultItem>> results = searchRepository.globalSearch(
                    searchTerm.trim(), currentUserId, page * limit, limit);

            // Filter to only users
            List<SearchResultItem> users = results.getOrDefault("users", Collections.emptyList());
            results = Map.of("users", users, "conversations", Collections.emptyList());

            return buildResponse(results, searchTerm, page, limit, startTime, false, false);

        } catch (Exception e) {
            logger.error("User search error: {}", e.getMessage());
            return unexpectedErrorResponse(searchTerm, startTime);
        }
    }

    /**
     * Search only conversations
     */
    public GlobalSearchResponse searchConversations(String searchTerm, String currentUserId, int page, int limit) {
        long startTime = System.currentTimeMillis();

        if (!isValidSearchTerm(searchTerm)) {
            return emptyResponse(searchTerm, page, limit, startTime, false);
        }

        page = Math.max(0, page);
        limit = Math.min(Math.max(1, limit), MAX_LIMIT);

        try {
            Map<String, List<SearchResultItem>> results = searchRepository.globalSearch(
                    searchTerm.trim(), currentUserId, page * limit, limit);

            // Filter to only conversations
            List<SearchResultItem> conversations = results.getOrDefault("conversations", Collections.emptyList());
            results = Map.of("users", Collections.emptyList(), "conversations", conversations);

            return buildResponse(results, searchTerm, page, limit, startTime, false, false);

        } catch (Exception e) {
            logger.error("Conversation search error: {}", e.getMessage());
            return unexpectedErrorResponse(searchTerm, startTime);
        }
    }

    // ==================== Response Builders ====================

    private boolean isValidSearchTerm(String searchTerm) {
        return searchTerm != null && searchTerm.trim().length() >= 2;
    }

    private GlobalSearchResponse buildResponse(
            Map<String, List<SearchResultItem>> results,
            String searchTerm, int page, int limit,
            long startTime, boolean isTypeahead, boolean fromCache) {

        List<SearchResultItem> users = results.getOrDefault("users", Collections.emptyList());
        List<SearchResultItem> conversations = results.getOrDefault("conversations", Collections.emptyList());

        // Build grouped results
        GroupedResults grouped = GroupedResults.builder()
                .users(users)
                .conversations(conversations)
                .messages(Collections.emptyList()) // Future: add message search
                .files(Collections.emptyList())    // Future: add file search
                .userCount(users.size())
                .conversationCount(conversations.size())
                .messageCount(0)
                .fileCount(0)
                .build();

        // Build combined results sorted by relevance score
        List<SearchResultItem> combined = Stream.concat(users.stream(), conversations.stream())
                .sorted(Comparator.comparingDouble(SearchResultItem::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        // Count by type
        Map<String, Integer> countByType = new HashMap<>();
        countByType.put("users", users.size());
        countByType.put("conversations", conversations.size());

        return GlobalSearchResponse.builder()
                .results(grouped)
                .combined(combined)
                .metadata(SearchMetadata.builder()
                        .searchTerm(searchTerm)
                        .totalCount(users.size() + conversations.size())
                        .page(page)
                        .limit(limit)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .fromCache(fromCache)
                        .isTypeahead(isTypeahead)
                        .countByType(countByType)
                        .build())
                .build();
    }

    private GlobalSearchResponse emptyResponse(String searchTerm, int page, int limit, long startTime, boolean isTypeahead) {
        return GlobalSearchResponse.builder()
                .results(GroupedResults.builder()
                        .users(Collections.emptyList())
                        .conversations(Collections.emptyList())
                        .messages(Collections.emptyList())
                        .files(Collections.emptyList())
                        .build())
                .combined(Collections.emptyList())
                .metadata(SearchMetadata.builder()
                        .searchTerm(searchTerm != null ? searchTerm : "")
                        .totalCount(0)
                        .page(page)
                        .limit(limit)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .isTypeahead(isTypeahead)
                        .countByType(Collections.emptyMap())
                        .build())
                .build();
    }

    private GlobalSearchResponse errorResponse(GlobalSearchException e, String searchTerm, long startTime) {
        return GlobalSearchResponse.builder()
                .results(GroupedResults.builder()
                        .users(Collections.emptyList())
                        .conversations(Collections.emptyList())
                        .messages(Collections.emptyList())
                        .files(Collections.emptyList())
                        .build())
                .combined(Collections.emptyList())
                .error(GlobalSearchResponse.ErrorInfo.builder()
                        .code(e.getErrorType().name())
                        .message(e.getErrorType().getMessage())
                        .retriable(e.isRetriable())
                        .build())
                .metadata(SearchMetadata.builder()
                        .searchTerm(searchTerm)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build())
                .build();
    }

    private GlobalSearchResponse unexpectedErrorResponse(String searchTerm, long startTime) {
        return GlobalSearchResponse.builder()
                .error(GlobalSearchResponse.ErrorInfo.builder()
                        .code("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .retriable(true)
                        .build())
                .metadata(SearchMetadata.builder()
                        .searchTerm(searchTerm)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build())
                .build();
    }
}