package bt.conference.repository;

import bt.conference.entity.Conversation;
import bt.conference.entity.UserCache;
import bt.conference.model.SearchResultItem;
import bt.conference.service.GlobalSearchException;
import bt.conference.service.GlobalSearchException.ErrorType;
import com.fierhub.model.UserSession;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
public class GlobalSearchRepository {

    private static final Logger logger = LoggerFactory.getLogger(GlobalSearchRepository.class);

    private final UserSession userSession;
    private final MongoTemplate mongoTemplate;

    // Thread pool configuration
    @Value("${search.thread.core-pool-size:4}")
    private int corePoolSize;

    @Value("${search.thread.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${search.thread.queue-capacity:200}")
    private int queueCapacity;

    @Value("${search.thread.keep-alive-seconds:60}")
    private long keepAliveSeconds;

    @Value("${search.timeout-seconds:5}")
    private int searchTimeoutSeconds;

    @Value("${search.typeahead.timeout-ms:1000}")
    private int typeaheadTimeoutMs;

    @Value("${search.retry.max-attempts:2}")
    private int maxRetryAttempts;

    @Value("${search.retry.delay-ms:50}")
    private long retryDelayMs;

    @Value("${search.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${search.cache.ttl-seconds:30}")
    private int cacheTtlSeconds;

    @Value("${search.use-text-index:false}")
    private boolean useTextIndex;

    // Thread pool and state
    private volatile ThreadPoolExecutor searchExecutor;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    // Simple in-memory cache (consider Redis for production)
    private final ConcurrentHashMap<String, CacheEntry> searchCache = new ConcurrentHashMap<>();

    // Metrics
    private final AtomicLong totalSearches = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong failedSearches = new AtomicLong(0);
    private final AtomicInteger activeSearches = new AtomicInteger(0);

    // Circuit breaker
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final long CIRCUIT_BREAKER_RESET_MS = 30000;

    // Rate limiter (simple per-user)
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimiter = new ConcurrentHashMap<>();
    private static final int RATE_LIMIT_REQUESTS = 30; // requests per window
    private static final long RATE_LIMIT_WINDOW_MS = 10000; // 10 seconds

    @Autowired
    public GlobalSearchRepository(UserSession userSession, MongoTemplate mongoTemplate) {
        this.userSession = userSession;
        this.mongoTemplate = mongoTemplate;
    }

    // ==================== Initialization & Shutdown ====================

    @PostConstruct
    public synchronized void init() {
        if (isInitialized.get()) {
            return;
        }

        try {
            logger.info("Initializing GlobalSearchRepository...");

            ThreadFactory threadFactory = r -> {
                Thread t = new Thread(r, "global-search-" + System.nanoTime());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    logger.error("Uncaught exception in {}: {}", thread.getName(), ex.getMessage(), ex);
                    consecutiveFailures.incrementAndGet();
                    lastFailureTime.set(System.currentTimeMillis());
                });
                return t;
            };

            RejectedExecutionHandler rejectionHandler = (runnable, executor) -> {
                logger.warn("Task rejected - pool exhausted. Queue: {}, Active: {}",
                        executor.getQueue().size(), executor.getActiveCount());
                try {
                    if (!executor.getQueue().offer(runnable, 500, TimeUnit.MILLISECONDS)) {
                        throw new RejectedExecutionException("Queue full");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RejectedExecutionException("Interrupted", e);
                }
            };

            searchExecutor = new ThreadPoolExecutor(
                    corePoolSize, maxPoolSize,
                    keepAliveSeconds, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueCapacity),
                    threadFactory, rejectionHandler
            );
            searchExecutor.allowCoreThreadTimeOut(true);
            searchExecutor.prestartAllCoreThreads();

            isInitialized.set(true);
            logger.info("GlobalSearchRepository initialized with {} core threads", corePoolSize);

            // Log index recommendations
            logIndexRecommendations();

        } catch (Exception e) {
            logger.error("Failed to initialize GlobalSearchRepository", e);
            throw new RuntimeException("Failed to initialize search", e);
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (!isInitialized.get() || isShuttingDown.getAndSet(true)) {
            return;
        }

        logger.info("Shutting down GlobalSearchRepository...");

        if (searchExecutor != null) {
            searchExecutor.shutdown();
            try {
                if (!searchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    List<Runnable> pending = searchExecutor.shutdownNow();
                    logger.warn("Force shutdown, cancelled {} tasks", pending.size());
                    searchExecutor.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                searchExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        searchCache.clear();
        logger.info("GlobalSearchRepository shutdown complete. Stats: searches={}, cacheHits={}, failures={}",
                totalSearches.get(), cacheHits.get(), failedSearches.get());
    }

    private void logIndexRecommendations() {
        logger.info("=== MongoDB Index Recommendations for Optimal Search Performance ===");
        logger.info("Run these commands in MongoDB shell:");
        logger.info("");
        logger.info("// Text indexes for full-text search (RECOMMENDED)");
        logger.info("db.user_cache.createIndex({");
        logger.info("  first_name: 'text', last_name: 'text', email: 'text', username: 'text'");
        logger.info("}, { name: 'user_text_search', weights: { first_name: 10, last_name: 10, username: 5, email: 3 }});");
        logger.info("");
        logger.info("db.conversation.createIndex({");
        logger.info("  conversation_name: 'text', 'participants.username': 'text', 'participants.email': 'text'");
        logger.info("}, { name: 'conversation_text_search' });");
        logger.info("");
        logger.info("// Compound indexes for filtering");
        logger.info("db.user_cache.createIndex({ is_active: 1, updated_at: -1 });");
        logger.info("db.conversation.createIndex({ is_active: 1, 'participant_ids': 1, updated_at: -1 });");
        logger.info("================================================================");
    }

    // ==================== Health & Metrics ====================

    public boolean isHealthy() {
        return isInitialized.get() && !isShuttingDown.get()
                && searchExecutor != null && !searchExecutor.isShutdown()
                && !isCircuitBreakerOpen();
    }

    private boolean isCircuitBreakerOpen() {
        if (consecutiveFailures.get() >= CIRCUIT_BREAKER_THRESHOLD) {
            if (System.currentTimeMillis() - lastFailureTime.get() < CIRCUIT_BREAKER_RESET_MS) {
                return true;
            }
            consecutiveFailures.set(0);
        }
        return false;
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalSearches", totalSearches.get());
        metrics.put("cacheHits", cacheHits.get());
        metrics.put("cacheHitRate", totalSearches.get() > 0
                ? (double) cacheHits.get() / totalSearches.get() * 100 : 0);
        metrics.put("failedSearches", failedSearches.get());
        metrics.put("activeSearches", activeSearches.get());
        metrics.put("cacheSize", searchCache.size());
        metrics.put("isHealthy", isHealthy());

        if (searchExecutor != null) {
            metrics.put("poolSize", searchExecutor.getPoolSize());
            metrics.put("activeThreads", searchExecutor.getActiveCount());
            metrics.put("queueSize", searchExecutor.getQueue().size());
        }
        return metrics;
    }

    // ==================== Rate Limiting ====================

    private void checkRateLimit(String userId) {
        if (userId == null) return;

        rateLimiter.compute(userId, (key, entry) -> {
            long now = System.currentTimeMillis();
            if (entry == null || now - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
                return new RateLimitEntry(now, 1);
            }
            if (entry.count >= RATE_LIMIT_REQUESTS) {
                throw new GlobalSearchException(ErrorType.RATE_LIMITED, "");
            }
            entry.count++;
            return entry;
        });

        // Cleanup old entries periodically
        if (rateLimiter.size() > 10000) {
            long now = System.currentTimeMillis();
            rateLimiter.entrySet().removeIf(e ->
                    now - e.getValue().windowStart > RATE_LIMIT_WINDOW_MS * 2);
        }
    }

    // ==================== Caching ====================

    private String buildCacheKey(String searchTerm, String userId, boolean isTypeahead, int limit) {
        return String.format("%s:%s:%s:%d", searchTerm.toLowerCase(), userId, isTypeahead, limit);
    }

    private SearchResultItem getFromCache(String cacheKey) {
        if (!cacheEnabled) return null;

        CacheEntry entry = searchCache.get(cacheKey);
        if (entry != null && !entry.isExpired(cacheTtlSeconds)) {
            cacheHits.incrementAndGet();
            return entry.results;
        }
        searchCache.remove(cacheKey);
        return null;
    }

    private void putInCache(String cacheKey, SearchResultItem results) {
        if (!cacheEnabled) return;

        searchCache.put(cacheKey, new CacheEntry(results));

        // Limit cache size
        if (searchCache.size() > 5000) {
            // Remove oldest 20%
            searchCache.entrySet().stream()
                    .sorted(Comparator.comparingLong(e -> e.getValue().createdAt))
                    .limit(1000)
                    .map(Map.Entry::getKey)
                    .forEach(searchCache::remove);
        }
    }

    // ==================== Main Search Methods ====================

    /**
     * Typeahead search - fast, limited results, prefix matching
     * Used as user types in search box
     */
    public SearchResultItem typeaheadSearch(String searchTerm, String userId, String fullSearch, int limit) {
        validateAndPrepare(searchTerm, userId);

        String cacheKey = buildCacheKey(searchTerm, userId, true, limit);
        SearchResultItem cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            SearchResultItem results =
                    executeParallelSearch(searchTerm, userId, 0, limit, fullSearch, true);
            putInCache(cacheKey, results);
            return results;
        } finally {
            activeSearches.decrementAndGet();
        }
    }

    /**
     * Full global search with pagination
     * Used when user presses Enter or clicks "See all results"
     */
    public SearchResultItem globalSearch(
            String searchTerm, String fullSearch, int skip, int limit) {

        validateAndPrepare(searchTerm, userSession.getUserId());

        String cacheKey = buildCacheKey(searchTerm + ":" + skip, userSession.getUserId(), false, limit);
        SearchResultItem cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            SearchResultItem results = executeParallelSearch(
                    searchTerm, userSession.getUserId(), skip, limit, fullSearch, false);
            putInCache(cacheKey, results);
            return results;
        } finally {
            activeSearches.decrementAndGet();
        }
    }

    private void validateAndPrepare(String searchTerm, String userId) {
        if (searchTerm == null || searchTerm.trim().length() < 2) {
            throw new GlobalSearchException(ErrorType.INVALID_INPUT, searchTerm);
        }
        if (!isInitialized.get() || isShuttingDown.get()) {
            throw new GlobalSearchException(ErrorType.THREAD_POOL_SHUTDOWN, searchTerm);
        }
        if (isCircuitBreakerOpen()) {
            throw new GlobalSearchException(ErrorType.THREAD_POOL_EXHAUSTED, searchTerm);
        }

        checkRateLimit(userId);
        totalSearches.incrementAndGet();
        activeSearches.incrementAndGet();
    }

    // ==================== Parallel Search Execution ====================
    private SearchResultItem executeParallelSearch(
            String searchTerm, String userId, int skip, int limit, String fullSearch, boolean isTypeAhead) {

        int timeoutMs = isTypeAhead ? typeaheadTimeoutMs : searchTimeoutSeconds * 1000;

        // Launch parallel searches
        CompletableFuture<List<UserCache>> usersFuture = CompletableFuture
                .supplyAsync(() -> searchUsers(searchTerm, fullSearch.equals("y"), skip, limit), searchExecutor)
                .exceptionally(ex -> {
                    logger.warn("User search failed: {}", ex.getMessage());
                    return Collections.emptyList();
                });

        CompletableFuture<List<Conversation>> conversationsFuture = CompletableFuture
                .supplyAsync(() -> searchConversations(searchTerm, userId, skip, limit), searchExecutor)
                .exceptionally(ex -> {
                    logger.warn("Conversation search failed: {}", ex.getMessage());
                    return Collections.emptyList();
                });

        try {
            CompletableFuture.allOf(usersFuture, conversationsFuture)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);

            consecutiveFailures.set(0); // Reset circuit breaker

            return SearchResultItem.builder()
                    .conversation(conversationsFuture.join())
                    .userCache(usersFuture.join())
                    .build();
        } catch (TimeoutException e) {
            logger.warn("Search timeout after {}ms for term: {}", timeoutMs, searchTerm);
            // Return partial results if available

            return SearchResultItem.builder()
                    .conversation(Collections.emptyList())
                    .userCache(Collections.emptyList())
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GlobalSearchException(ErrorType.THREAD_INTERRUPTED, searchTerm, e);

        } catch (ExecutionException e) {
            recordFailure();
            throw new GlobalSearchException(ErrorType.EXECUTION_FAILED, searchTerm, e.getCause());
        }
    }

    private List<SearchResultItem> getCompletedResult(CompletableFuture<List<SearchResultItem>> future) {
        try {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                return future.getNow(Collections.emptyList());
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private void recordFailure() {
        consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        failedSearches.incrementAndGet();
    }

    // ==================== User Search ====================

    private List<UserCache> searchUsers(String searchTerm, boolean required, int skip, int limit) {
        if (!required) {
            return new ArrayList<>();
        }

        try {
            Query query;

            if (useTextIndex) {
                // Use MongoDB text search (requires text index)
                TextCriteria textCriteria = TextCriteria.forDefaultLanguage()
                        .matchingPhrase(searchTerm);
                query = TextQuery.queryText(textCriteria)
                        .sortByScore();
            } else {
                // Fallback to regex (slower but works without index)
                Pattern pattern = Pattern.compile("^" + Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE);
                query = new Query(new Criteria().andOperator(
                        Criteria.where("is_active").is(true),
                        new Criteria().orOperator(
                                Criteria.where("first_name").regex(pattern),
                                Criteria.where("last_name").regex(pattern),
                                Criteria.where("email").regex(pattern),
                                Criteria.where("username").regex(pattern)
                        )
                ));
            }

            query.addCriteria(Criteria.where("is_active").is(true));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "updated_at"));

            return mongoTemplate.find(query, UserCache.class, "user_cache");

            //return docs.stream()
            //        .map(doc -> mapToUserResult(doc, searchTerm))
            //        .collect(Collectors.toList());
        } catch (MongoTimeoutException e) {
            throw new GlobalSearchException(ErrorType.TIMEOUT, searchTerm, e);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, searchTerm, e);
        }
    }

    private UserCache mapToUserResult(Document doc, String searchTerm) {
        String firstName = doc.getString("first_name");
        String lastName = doc.getString("last_name");
        String fullName = (firstName != null ? firstName : "") +
                (lastName != null ? " " + lastName : "");

        // Calculate simple relevance score
        double score = calculateRelevanceScore(searchTerm,
                firstName, lastName, doc.getString("email"), doc.getString("username"));

        // Build highlight map
        Map<String, String> highlights = buildHighlights(searchTerm,
                Map.of("name", fullName,
                        "email", doc.getString("email") != null ? doc.getString("email") : "",
                        "username", doc.getString("username") != null ? doc.getString("username") : ""));

        return UserCache.builder()
                //.type(SearchResultItem.ResultType.USER)
                //.id(doc.getString("user_id"))
                //.title(fullName.trim())
                //.subtitle(doc.getString("email"))
                //.avatar(doc.getString("avatar"))
                //.status(doc.getString("status"))
                //.score(score)
                //.highlights(highlights)
                //.lastActivity(doc.getDate("updated_at") != null
                //       ? doc.getDate("updated_at").toInstant() : null)
                //.metadata(Map.of("username", doc.getString("username") != null ? doc.getString("username") : ""))
                .build();
    }

    // ==================== Conversation Search ====================

    private List<Conversation> searchConversations(String searchTerm, String userId, int skip, int limit) {
        try {
            Query query;

            if (useTextIndex) {
                TextCriteria textCriteria = TextCriteria.forDefaultLanguage()
                        .matchingPhrase(searchTerm);
                query = TextQuery.queryText(textCriteria).sortByScore();
            } else {
                Pattern pattern = Pattern.compile("^" + Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE);
                query = new Query(new Criteria().andOperator(
                        Criteria.where("is_active").is(true),
                        new Criteria().orOperator(
                                Criteria.where("conversation_name").regex(pattern),
                                Criteria.where("participants.username").regex(pattern),
                                Criteria.where("participants.email").regex(pattern)
                        )
                ));
            }

            // SECURITY: Only show conversations user is part of
            if (userId != null) {
                query.addCriteria(Criteria.where("participant_ids").in(userId));
            }

            query.addCriteria(Criteria.where("is_active").is(true));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "updated_at"));

            return mongoTemplate.find(query, Conversation.class, "conversations");
        } catch (MongoTimeoutException e) {
            throw new GlobalSearchException(ErrorType.TIMEOUT, searchTerm, e);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, searchTerm, e);
        }
    }

//    private SearchResultItem mapToConversationResult(Document doc, String searchTerm, String currentUserId) {
//        String conversationName = doc.getString("conversation_name");
//        String conversationType = doc.getString("conversation_type");
//
//        // For direct chats, show other participant's name
//        String displayName = conversationName;
//        String subtitle = conversationType;
//
//        @SuppressWarnings("unchecked")
//        List<Document> participants = (List<Document>) doc.get("participants");
//        if ("direct".equals(conversationType) && participants != null && currentUserId != null) {
//            for (Document p : participants) {
//                if (!currentUserId.equals(p.getString("user_id"))) {
//                    displayName = p.getString("username");
//                    subtitle = p.getString("email");
//                    break;
//                }
//            }
//        }
//
//        double score = calculateRelevanceScore(searchTerm, conversationName, displayName, null, null);
//
//        return SearchResultItem.builder()
//                .type(SearchResultItem.ResultType.CONVERSATION)
//                .id(doc.getObjectId("_id").toString())
//                .title(displayName)
//                .subtitle(subtitle)
//                .score(score)
//                .lastActivity(doc.getDate("updated_at") != null
//                        ? doc.getDate("updated_at").toInstant() : null)
//                .metadata(Map.of(
//                        "type", conversationType != null ? conversationType : "unknown",
//                        "participantCount", participants != null ? participants.size() : 0
//                ))
//                .build();
//    }

    // ==================== Relevance & Highlighting ====================

    private double calculateRelevanceScore(String searchTerm, String... fields) {
        double score = 0;
        String searchLower = searchTerm.toLowerCase();

        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            if (field == null) continue;

            String fieldLower = field.toLowerCase();
            double weight = 1.0 / (i + 1); // Earlier fields have higher weight

            if (fieldLower.equals(searchLower)) {
                score += 100 * weight; // Exact match
            } else if (fieldLower.startsWith(searchLower)) {
                score += 80 * weight; // Prefix match
            } else if (fieldLower.contains(searchLower)) {
                score += 50 * weight; // Contains match
            }
        }

        return score;
    }

    private Map<String, String> buildHighlights(String searchTerm, Map<String, String> fields) {
        Map<String, String> highlights = new HashMap<>();
        String searchLower = searchTerm.toLowerCase();

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.toLowerCase().contains(searchLower)) {
                // Simple highlight: wrap match in <mark> tags
                String highlighted = value.replaceAll(
                        "(?i)(" + Pattern.quote(searchTerm) + ")",
                        "<mark>$1</mark>");
                highlights.put(entry.getKey(), highlighted);
            }
        }

        return highlights.isEmpty() ? null : highlights;
    }

    // ==================== Helper Classes ====================

    private static class CacheEntry {
        final SearchResultItem results;
        final long createdAt;

        CacheEntry(SearchResultItem results) {
            this.results = results;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(int ttlSeconds) {
            return System.currentTimeMillis() - createdAt > ttlSeconds * 1000L;
        }
    }

    private static class RateLimitEntry {
        long windowStart;
        int count;

        RateLimitEntry(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}