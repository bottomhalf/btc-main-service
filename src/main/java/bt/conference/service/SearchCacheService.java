package bt.conference.service;

import bt.conference.config.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generic in-memory cache service for search results.
 * Can be extended to use Redis for production environments.
 */
@Service
public class SearchCacheService {

    private static final Logger logger = LoggerFactory.getLogger(SearchCacheService.class);

    private final SearchConfig config;
    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

    // Metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheEvictions = new AtomicLong(0);

    @Autowired
    public SearchCacheService(SearchConfig config) {
        this.config = config;
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Clearing search cache. Stats: hits={}, misses={}, evictions={}",
                cacheHits.get(), cacheMisses.get(), cacheEvictions.get());
        cache.clear();
    }

    /**
     * Build a cache key with namespace to avoid collisions between different repositories.
     */
    public String buildKey(String namespace, String searchTerm, String userId,
                           boolean isTypeahead, int skip, int limit) {
        return String.format("%s:%s:%s:%s:%d:%d",
                namespace,
                searchTerm.toLowerCase().trim(),
                userId != null ? userId : "anon",
                isTypeahead,
                skip,
                limit);
    }

    /**
     * Get value from cache if exists and not expired.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (!config.isCacheEnabled()) {
            return null;
        }

        CacheEntry<?> entry = cache.get(key);
        if (entry == null) {
            cacheMisses.incrementAndGet();
            return null;
        }

        if (entry.isExpired(config.getCacheTtlSeconds())) {
            cache.remove(key);
            cacheMisses.incrementAndGet();
            return null;
        }

        cacheHits.incrementAndGet();
        return (T) entry.value;
    }

    /**
     * Put value in cache with automatic eviction when full.
     */
    public <T> void put(String key, T value) {
        if (!config.isCacheEnabled() || value == null) {
            return;
        }

        cache.put(key, new CacheEntry<>(value));

        // Evict old entries if cache is too large
        if (cache.size() > config.getMaxCacheSize()) {
            evictOldEntries();
        }
    }

    /**
     * Invalidate a specific cache entry.
     */
    public void invalidate(String key) {
        cache.remove(key);
    }

    /**
     * Invalidate all entries matching a prefix (namespace-based invalidation).
     */
    public void invalidateByPrefix(String prefix) {
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Clear all cache entries.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        logger.info("Cleared {} cache entries", size);
    }

    /**
     * Evict oldest entries when cache is full.
     */
    private void evictOldEntries() {
        int toEvict = (int) (config.getMaxCacheSize() * config.getCacheEvictionRatio());

        cache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().createdAt))
                .limit(toEvict)
                .map(Map.Entry::getKey)
                .forEach(key -> {
                    cache.remove(key);
                    cacheEvictions.incrementAndGet();
                });

        logger.debug("Evicted {} cache entries", toEvict);
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;

        stats.put("size", cache.size());
        stats.put("maxSize", config.getMaxCacheSize());
        stats.put("hits", hits);
        stats.put("misses", misses);
        stats.put("evictions", cacheEvictions.get());
        stats.put("hitRate", total > 0 ? (double) hits / total * 100 : 0);
        stats.put("enabled", config.isCacheEnabled());
        stats.put("ttlSeconds", config.getCacheTtlSeconds());

        return stats;
    }

    public long getHits() { return cacheHits.get(); }
    public long getMisses() { return cacheMisses.get(); }
    public int getSize() { return cache.size(); }

    // ==================== Cache Entry ====================

    private static class CacheEntry<T> {
        final T value;
        final long createdAt;

        CacheEntry(T value) {
            this.value = value;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(int ttlSeconds) {
            return System.currentTimeMillis() - createdAt > ttlSeconds * 1000L;
        }
    }
}