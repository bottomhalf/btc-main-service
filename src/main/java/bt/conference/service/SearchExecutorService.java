package bt.conference.service;

import bt.conference.config.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Shared executor service for all search operations.
 * Manages thread pool, circuit breaker, and rate limiting.
 */
@Service
public class SearchExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(SearchExecutorService.class);

    private final SearchConfig config;

    private volatile ThreadPoolExecutor executor;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    // Circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    // Rate limiter
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimiter = new ConcurrentHashMap<>();

    // Metrics
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong failedExecutions = new AtomicLong(0);
    private final AtomicInteger activeExecutions = new AtomicInteger(0);

    @Autowired
    public SearchExecutorService(SearchConfig config) {
        this.config = config;
    }

    @PostConstruct
    public synchronized void init() {
        if (isInitialized.get()) {
            return;
        }

        try {
            logger.info("Initializing SearchExecutorService...");

            ThreadFactory threadFactory = r -> {
                Thread t = new Thread(r, "search-executor-" + System.nanoTime());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    logger.error("Uncaught exception in {}: {}", thread.getName(), ex.getMessage(), ex);
                    recordFailure();
                });
                return t;
            };

            RejectedExecutionHandler rejectionHandler = (runnable, pool) -> {
                logger.warn("Task rejected - pool exhausted. Queue: {}, Active: {}",
                        pool.getQueue().size(), pool.getActiveCount());
                try {
                    if (!pool.getQueue().offer(runnable, 500, TimeUnit.MILLISECONDS)) {
                        throw new RejectedExecutionException("Queue full after waiting");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RejectedExecutionException("Interrupted while waiting", e);
                }
            };

            executor = new ThreadPoolExecutor(
                    config.getCorePoolSize(),
                    config.getMaxPoolSize(),
                    config.getKeepAliveSeconds(),
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(config.getQueueCapacity()),
                    threadFactory,
                    rejectionHandler
            );
            executor.allowCoreThreadTimeOut(true);
            executor.prestartAllCoreThreads();

            isInitialized.set(true);
            logger.info("SearchExecutorService initialized: corePool={}, maxPool={}, queueCapacity={}",
                    config.getCorePoolSize(), config.getMaxPoolSize(), config.getQueueCapacity());

        } catch (Exception e) {
            logger.error("Failed to initialize SearchExecutorService", e);
            throw new RuntimeException("Failed to initialize search executor", e);
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (!isInitialized.get() || isShuttingDown.getAndSet(true)) {
            return;
        }

        logger.info("Shutting down SearchExecutorService...");

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    List<Runnable> pending = executor.shutdownNow();
                    logger.warn("Force shutdown, cancelled {} pending tasks", pending.size());
                    executor.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        rateLimiter.clear();
        logger.info("SearchExecutorService shutdown complete. Stats: total={}, failed={}",
                totalExecutions.get(), failedExecutions.get());
    }

    // ==================== Execution Methods ====================

    /**
     * Submit a task for async execution with timeout handling.
     */
    public <T> CompletableFuture<T> submitAsync(Supplier<T> task, String taskName) {
        ensureReady();
        totalExecutions.incrementAndGet();
        activeExecutions.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            try {
                T result = task.get();
                resetCircuitBreaker();
                return result;
            } catch (Exception e) {
                recordFailure();
                throw e;
            } finally {
                activeExecutions.decrementAndGet();
            }
        }, executor).exceptionally(ex -> {
            logger.warn("Task '{}' failed: {}", taskName, ex.getMessage());
            return null;
        });
    }

    /**
     * Execute multiple tasks in parallel and wait for completion.
     */
    @SafeVarargs
    public final <T> List<T> executeParallel(int timeoutMs, Supplier<T>... tasks) {
        ensureReady();

        @SuppressWarnings("unchecked")
        CompletableFuture<T>[] futures = new CompletableFuture[tasks.length];

        for (int i = 0; i < tasks.length; i++) {
            final int index = i;
            futures[i] = submitAsync(tasks[i], "parallel-task-" + index);
        }

        try {
            CompletableFuture.allOf(futures).get(timeoutMs, TimeUnit.MILLISECONDS);
            resetCircuitBreaker();
        } catch (TimeoutException e) {
            logger.warn("Parallel execution timed out after {}ms", timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GlobalSearchException(GlobalSearchException.ErrorType.THREAD_INTERRUPTED, "parallel", e);
        } catch (ExecutionException e) {
            recordFailure();
            throw new GlobalSearchException(GlobalSearchException.ErrorType.EXECUTION_FAILED, "parallel", e.getCause());
        }

        return java.util.Arrays.stream(futures)
                .map(f -> {
                    try {
                        return f.isDone() && !f.isCompletedExceptionally() ? f.getNow(null) : null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Execute a task with retry logic.
     */
    public <T> T executeWithRetry(Supplier<T> task, String taskName) {
        ensureReady();

        Exception lastException = null;
        for (int attempt = 0; attempt <= config.getMaxRetryAttempts(); attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(config.getRetryDelayMs() * attempt);
                    logger.debug("Retry attempt {} for task '{}'", attempt, taskName);
                }
                T result = task.get();
                resetCircuitBreaker();
                return result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GlobalSearchException(GlobalSearchException.ErrorType.THREAD_INTERRUPTED, taskName, e);
            } catch (Exception e) {
                lastException = e;
                recordFailure();
            }
        }

        throw new GlobalSearchException(GlobalSearchException.ErrorType.EXECUTION_FAILED,
                taskName, lastException);
    }

    // ==================== Rate Limiting ====================

    public void checkRateLimit(String userId) {
        if (userId == null) return;

        rateLimiter.compute(userId, (key, entry) -> {
            long now = System.currentTimeMillis();
            if (entry == null || now - entry.windowStart > config.getRateLimitWindowMs()) {
                return new RateLimitEntry(now, 1);
            }
            if (entry.count >= config.getRateLimitRequests()) {
                throw new GlobalSearchException(GlobalSearchException.ErrorType.RATE_LIMITED, "");
            }
            entry.count++;
            return entry;
        });

        // Cleanup old entries periodically
        if (rateLimiter.size() > config.getMaxRateLimitEntries()) {
            long now = System.currentTimeMillis();
            rateLimiter.entrySet().removeIf(e ->
                    now - e.getValue().windowStart > config.getRateLimitWindowMs() * 2);
        }
    }

    // ==================== Circuit Breaker ====================

    public boolean isCircuitBreakerOpen() {
        if (consecutiveFailures.get() >= config.getCircuitBreakerThreshold()) {
            if (System.currentTimeMillis() - lastFailureTime.get() < config.getCircuitBreakerResetMs()) {
                return true;
            }
            resetCircuitBreaker();
        }
        return false;
    }

    public void recordFailure() {
        consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        failedExecutions.incrementAndGet();
    }

    public void resetCircuitBreaker() {
        consecutiveFailures.set(0);
    }

    // ==================== Health & Metrics ====================

    public boolean isHealthy() {
        return isInitialized.get()
                && !isShuttingDown.get()
                && executor != null
                && !executor.isShutdown()
                && !isCircuitBreakerOpen();
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalExecutions", totalExecutions.get());
        metrics.put("failedExecutions", failedExecutions.get());
        metrics.put("activeExecutions", activeExecutions.get());
        metrics.put("consecutiveFailures", consecutiveFailures.get());
        metrics.put("isHealthy", isHealthy());
        metrics.put("isCircuitBreakerOpen", isCircuitBreakerOpen());

        if (executor != null) {
            metrics.put("poolSize", executor.getPoolSize());
            metrics.put("activeThreads", executor.getActiveCount());
            metrics.put("queueSize", executor.getQueue().size());
            metrics.put("completedTasks", executor.getCompletedTaskCount());
        }
        return metrics;
    }

    private void ensureReady() {
        if (!isInitialized.get() || isShuttingDown.get()) {
            throw new GlobalSearchException(GlobalSearchException.ErrorType.THREAD_POOL_SHUTDOWN, "executor");
        }
        if (isCircuitBreakerOpen()) {
            throw new GlobalSearchException(GlobalSearchException.ErrorType.THREAD_POOL_EXHAUSTED, "circuit-breaker");
        }
    }

    public ThreadPoolExecutor getExecutor() {
        return executor;
    }

    public SearchConfig getConfig() {
        return config;
    }

    // ==================== Helper Classes ====================

    private static class RateLimitEntry {
        long windowStart;
        int count;

        RateLimitEntry(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}