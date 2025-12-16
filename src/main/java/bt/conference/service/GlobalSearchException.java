package bt.conference.service;

/**
 * Custom exception for global search operations.
 */
public class GlobalSearchException extends RuntimeException {

    private final ErrorType errorType;
    private final String searchTerm;
    private final boolean retriable;

    public enum ErrorType {
        TIMEOUT("Search operation timed out", true),
        THREAD_INTERRUPTED("Search thread was interrupted", true),
        EXECUTION_FAILED("Search execution failed", false),
        THREAD_POOL_EXHAUSTED("Thread pool is exhausted", true),
        THREAD_POOL_SHUTDOWN("Thread pool has been shut down", false),
        DATABASE_ERROR("Database operation failed", true),
        INVALID_INPUT("Invalid search input", false),
        RATE_LIMITED("Too many requests, please slow down", true),
        UNAUTHORIZED("User not authorized for this search", false),
        UNKNOWN("Unknown error occurred", false);

        private final String message;
        private final boolean retriable;

        ErrorType(String message, boolean retriable) {
            this.message = message;
            this.retriable = retriable;
        }

        public String getMessage() {
            return message;
        }

        public boolean isRetriable() {
            return retriable;
        }
    }

    public GlobalSearchException(ErrorType errorType, String searchTerm, Throwable cause) {
        super(buildMessage(errorType, searchTerm), cause);
        this.errorType = errorType;
        this.searchTerm = searchTerm;
        this.retriable = errorType.isRetriable();
    }

    public GlobalSearchException(ErrorType errorType, String searchTerm) {
        super(buildMessage(errorType, searchTerm));
        this.errorType = errorType;
        this.searchTerm = searchTerm;
        this.retriable = errorType.isRetriable();
    }

    private static String buildMessage(ErrorType errorType, String searchTerm) {
        return String.format("%s [searchTerm=%s]", errorType.getMessage(), searchTerm);
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public boolean isRetriable() {
        return retriable;
    }
}