package bt.conference.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Centralized configuration for all search repositories.
 * Shared across GlobalSearchRepository, MessageSearchRepository, etc.
 */
@Configuration
@ConfigurationProperties(prefix = "search")
@Validated
public class SearchConfig {

    // ==================== Thread Pool Settings ====================

    @Min(1) @Max(32)
    private int corePoolSize = 4;

    @Min(1) @Max(64)
    private int maxPoolSize = 16;

    @Min(10) @Max(1000)
    private int queueCapacity = 200;

    @Positive
    private long keepAliveSeconds = 60;

    // ==================== Timeout Settings ====================

    @Min(1) @Max(60)
    private int timeoutSeconds = 5;

    @Min(100) @Max(10000)
    private int typeaheadTimeoutMs = 1000;

    // ==================== Retry Settings ====================

    @Min(0) @Max(5)
    private int maxRetryAttempts = 2;

    @Min(10) @Max(1000)
    private long retryDelayMs = 50;

    // ==================== Cache Settings ====================

    private boolean cacheEnabled = true;

    @Min(5) @Max(3600)
    private int cacheTtlSeconds = 30;

    @Min(100) @Max(100000)
    private int maxCacheSize = 5000;

    private double cacheEvictionRatio = 0.2; // Remove 20% when full

    // ==================== Search Behavior ====================

    private boolean useTextIndex = false;

    @Min(2) @Max(100)
    private int minSearchTermLength = 2;

    @Min(1) @Max(100)
    private int defaultPageSize = 20;

    @Min(1) @Max(500)
    private int maxPageSize = 100;

    // ==================== Circuit Breaker ====================

    @Min(1) @Max(20)
    private int circuitBreakerThreshold = 5;

    @Min(1000) @Max(300000)
    private long circuitBreakerResetMs = 30000;

    // ==================== Rate Limiting ====================

    @Min(1) @Max(1000)
    private int rateLimitRequests = 30;

    @Min(1000) @Max(60000)
    private long rateLimitWindowMs = 10000;

    @Min(100) @Max(100000)
    private int maxRateLimitEntries = 10000;

    // ==================== Getters and Setters ====================

    public int getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

    public long getKeepAliveSeconds() { return keepAliveSeconds; }
    public void setKeepAliveSeconds(long keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getTypeaheadTimeoutMs() { return typeaheadTimeoutMs; }
    public void setTypeaheadTimeoutMs(int typeaheadTimeoutMs) { this.typeaheadTimeoutMs = typeaheadTimeoutMs; }

    public int getMaxRetryAttempts() { return maxRetryAttempts; }
    public void setMaxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }

    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }

    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }

    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }

    public int getMaxCacheSize() { return maxCacheSize; }
    public void setMaxCacheSize(int maxCacheSize) { this.maxCacheSize = maxCacheSize; }

    public double getCacheEvictionRatio() { return cacheEvictionRatio; }
    public void setCacheEvictionRatio(double cacheEvictionRatio) { this.cacheEvictionRatio = cacheEvictionRatio; }

    public boolean isUseTextIndex() { return useTextIndex; }
    public void setUseTextIndex(boolean useTextIndex) { this.useTextIndex = useTextIndex; }

    public int getMinSearchTermLength() { return minSearchTermLength; }
    public void setMinSearchTermLength(int minSearchTermLength) { this.minSearchTermLength = minSearchTermLength; }

    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = defaultPageSize; }

    public int getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }

    public int getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    public void setCircuitBreakerThreshold(int circuitBreakerThreshold) { this.circuitBreakerThreshold = circuitBreakerThreshold; }

    public long getCircuitBreakerResetMs() { return circuitBreakerResetMs; }
    public void setCircuitBreakerResetMs(long circuitBreakerResetMs) { this.circuitBreakerResetMs = circuitBreakerResetMs; }

    public int getRateLimitRequests() { return rateLimitRequests; }
    public void setRateLimitRequests(int rateLimitRequests) { this.rateLimitRequests = rateLimitRequests; }

    public long getRateLimitWindowMs() { return rateLimitWindowMs; }
    public void setRateLimitWindowMs(long rateLimitWindowMs) { this.rateLimitWindowMs = rateLimitWindowMs; }

    public int getMaxRateLimitEntries() { return maxRateLimitEntries; }
    public void setMaxRateLimitEntries(int maxRateLimitEntries) { this.maxRateLimitEntries = maxRateLimitEntries; }

    public int getTimeoutMs() { return timeoutSeconds * 1000; }
}