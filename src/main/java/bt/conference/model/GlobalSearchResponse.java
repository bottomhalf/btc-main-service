package bt.conference.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response structure for global search, optimized for Teams-like UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlobalSearchResponse {

    /**
     * Grouped results by type (for tabbed UI: People, Chats, Messages, Files)
     */
    private GroupedResults results;

    /**
     * Combined results sorted by relevance (for unified search view)
     */
    private List<SearchResultItem> combined;

    /**
     * Search metadata
     */
    private SearchMetadata metadata;

    /**
     * Error information (if any)
     */
    private ErrorInfo error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupedResults {
        private List<SearchResultItem> users;
        private List<SearchResultItem> conversations;
        private List<SearchResultItem> messages;
        private List<SearchResultItem> files;

        private int userCount;
        private int conversationCount;
        private int messageCount;
        private int fileCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchMetadata {
        private String searchTerm;
        private long totalCount;
        private int page;
        private int limit;
        private long executionTimeMs;
        private boolean fromCache;
        private boolean isTypeahead;
        private Map<String, Integer> countByType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String code;
        private String message;
        private boolean retriable;
    }

    public boolean hasResults() {
        if (combined != null && !combined.isEmpty()) {
            return true;
        }
        if (results != null) {
            return (results.users != null && !results.users.isEmpty())
                    || (results.conversations != null && !results.conversations.isEmpty())
                    || (results.messages != null && !results.messages.isEmpty())
                    || (results.files != null && !results.files.isEmpty());
        }
        return false;
    }

    public boolean hasError() {
        return error != null;
    }

    public static GlobalSearchResponse error(String code, String message, boolean retriable) {
        return GlobalSearchResponse.builder()
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .retriable(retriable)
                        .build())
                .build();
    }
}