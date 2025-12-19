package bt.conference.repository;

import bt.conference.config.SearchConfig;
import bt.conference.service.GlobalSearchException;
import bt.conference.service.GlobalSearchException.ErrorType;

import bt.conference.service.SearchCacheService;
import bt.conference.service.SearchExecutorService;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Abstract base repository providing common search functionality.
 * Extend this class for entity-specific search repositories.
 */
public abstract class BaseSearchRepository<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final MongoTemplate mongoTemplate;
    protected final SearchExecutorService executorService;
    protected final SearchCacheService cacheService;
    protected final SearchConfig config;

    // Metrics
    protected final AtomicLong totalSearches = new AtomicLong(0);

    protected BaseSearchRepository(
            MongoTemplate mongoTemplate,
            SearchExecutorService executorService,
            SearchCacheService cacheService,
            SearchConfig config) {
        this.mongoTemplate = mongoTemplate;
        this.executorService = executorService;
        this.cacheService = cacheService;
        this.config = config;
    }

    // ==================== Abstract Methods ====================

    /**
     * Get the MongoDB collection name for this entity.
     */
    protected abstract String getCollectionName();

    /**
     * Get the entity class for this repository.
     */
    protected abstract Class<T> getEntityClass();

    /**
     * Get the cache namespace for this repository.
     */
    protected abstract String getCacheNamespace();

    /**
     * Build text search fields for this entity.
     * Return field names to be included in text search.
     */
    protected abstract String[] getTextSearchFields();

    /**
     * Build criteria for regex-based search (fallback when text index unavailable).
     */
    protected abstract Criteria buildRegexSearchCriteria(Pattern pattern);

    /**
     * Get the default sort field for this entity.
     */
    protected String getDefaultSortField() {
        return "updated_at";
    }

    /**
     * Get the default sort direction.
     */
    protected Sort.Direction getDefaultSortDirection() {
        return Sort.Direction.DESC;
    }

    // ==================== Common Search Methods ====================

    /**
     * Search with pagination and caching.
     */
    public List<T> search(String searchTerm, String userId, int skip, int limit) {
        validateSearchTerm(searchTerm);
        executorService.checkRateLimit(userId);
        totalSearches.incrementAndGet();

        String cacheKey = cacheService.buildKey(
                getCacheNamespace(), searchTerm, userId, false, skip, limit);

        List<T> cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<T> results = executeSearch(searchTerm, userId, skip, limit, null);
        cacheService.put(cacheKey, results);
        return results;
    }

    /**
     * Typeahead search - faster, limited results.
     */
    public List<T> typeaheadSearch(String searchTerm, String userId, int limit) {
        validateSearchTerm(searchTerm);
        executorService.checkRateLimit(userId);
        totalSearches.incrementAndGet();

        String cacheKey = cacheService.buildKey(
                getCacheNamespace(), searchTerm, userId, true, 0, limit);

        List<T> cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<T> results = executeSearch(searchTerm, userId, 0, limit, null);
        cacheService.put(cacheKey, results);
        return results;
    }

    /**
     * Find by ID.
     */
    public Optional<T> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            T result = mongoTemplate.findById(id, getEntityClass(), getCollectionName());
            return Optional.ofNullable(result);
        } catch (MongoException e) {
            logger.error("Error finding {} by id {}: {}", getCollectionName(), id, e.getMessage());
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, id, e);
        }
    }

    /**
     * Find by specific field value.
     */
    public List<T> findByField(String fieldName, Object value, int limit) {
        try {
            Query query = new Query(Criteria.where(fieldName).is(value));
            query.limit(limit);
            query.with(Sort.by(getDefaultSortDirection(), getDefaultSortField()));
            return mongoTemplate.find(query, getEntityClass(), getCollectionName());
        } catch (MongoException e) {
            logger.error("Error finding {} by {}: {}", getCollectionName(), fieldName, e.getMessage());
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, fieldName, e);
        }
    }

    /**
     * Find all with pagination.
     */
    public List<T> findAll(int skip, int limit) {
        try {
            Query query = new Query();
            query.skip(skip).limit(limit);
            query.with(Sort.by(getDefaultSortDirection(), getDefaultSortField()));
            return mongoTemplate.find(query, getEntityClass(), getCollectionName());
        } catch (MongoException e) {
            logger.error("Error finding all {}: {}", getCollectionName(), e.getMessage());
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, "findAll", e);
        }
    }

    /**
     * Count total documents matching criteria.
     */
    public long count(Criteria criteria) {
        try {
            Query query = criteria != null ? new Query(criteria) : new Query();
            return mongoTemplate.count(query, getEntityClass(), getCollectionName());
        } catch (MongoException e) {
            logger.error("Error counting {}: {}", getCollectionName(), e.getMessage());
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, "count", e);
        }
    }

    // ==================== Protected Helper Methods ====================

    /**
     * Execute the actual search query.
     * Override for custom search behavior.
     */
    protected List<T> executeSearch(String searchTerm, String userId,
                                    int skip, int limit, Criteria additionalCriteria) {
        try {
            Query query = buildSearchQuery(searchTerm, additionalCriteria);
            query.skip(skip).limit(limit);
            query.with(Sort.by(getDefaultSortDirection(), getDefaultSortField()));

            return mongoTemplate.find(query, getEntityClass(), getCollectionName());
        } catch (MongoTimeoutException e) {
            throw new GlobalSearchException(ErrorType.TIMEOUT, searchTerm, e);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, searchTerm, e);
        }
    }

    /**
     * Build search query using text index or regex fallback.
     */
    protected Query buildSearchQuery(String searchTerm, Criteria additionalCriteria) {
        Query query;

        if (config.isUseTextIndex()) {
            TextCriteria textCriteria = TextCriteria.forDefaultLanguage()
                    .matchingPhrase(searchTerm);
            query = TextQuery.queryText(textCriteria).sortByScore();
        } else {
            Pattern pattern = Pattern.compile(
                    "^" + Pattern.quote(searchTerm),
                    Pattern.CASE_INSENSITIVE
            );
            query = new Query(buildRegexSearchCriteria(pattern));
        }

        if (additionalCriteria != null) {
            query.addCriteria(additionalCriteria);
        }

        return query;
    }

    /**
     * Build regex pattern for case-insensitive prefix matching.
     */
    protected Pattern buildPrefixPattern(String searchTerm) {
        return Pattern.compile("^" + Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Build regex pattern for case-insensitive contains matching.
     */
    protected Pattern buildContainsPattern(String searchTerm) {
        return Pattern.compile(Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Validate search term.
     */
    protected void validateSearchTerm(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().length() < config.getMinSearchTermLength()) {
            throw new GlobalSearchException(ErrorType.INVALID_INPUT, searchTerm);
        }
    }

    /**
     * Calculate relevance score for result ranking.
     */
    protected double calculateRelevanceScore(String searchTerm, String... fields) {
        double score = 0;
        String searchLower = searchTerm.toLowerCase();

        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            if (field == null) continue;

            String fieldLower = field.toLowerCase();
            double weight = 1.0 / (i + 1);

            if (fieldLower.equals(searchLower)) {
                score += 100 * weight;
            } else if (fieldLower.startsWith(searchLower)) {
                score += 80 * weight;
            } else if (fieldLower.contains(searchLower)) {
                score += 50 * weight;
            }
        }

        return score;
    }

    /**
     * Build highlights for matched fields.
     */
    protected Map<String, String> buildHighlights(String searchTerm, Map<String, String> fields) {
        Map<String, String> highlights = new HashMap<>();
        String searchLower = searchTerm.toLowerCase();

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.toLowerCase().contains(searchLower)) {
                String highlighted = value.replaceAll(
                        "(?i)(" + Pattern.quote(searchTerm) + ")",
                        "<mark>$1</mark>");
                highlights.put(entry.getKey(), highlighted);
            }
        }

        return highlights.isEmpty() ? null : highlights;
    }

    // ==================== Metrics ====================

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("collection", getCollectionName());
        metrics.put("totalSearches", totalSearches.get());
        metrics.put("cacheStats", cacheService.getStats());
        return metrics;
    }

    /**
     * Log index recommendations for this repository.
     */
    public void logIndexRecommendations() {
        logger.info("=== MongoDB Index Recommendations for {} ===", getCollectionName());
        logger.info("// Text index for full-text search");
        logger.info("db.{}.createIndex({{", getCollectionName());
        for (String field : getTextSearchFields()) {
            logger.info("  {}: 'text',", field);
        }
        logger.info("}}, {{ name: '{}_text_search' }});", getCollectionName());
        logger.info("================================================");
    }
}