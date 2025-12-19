package bt.conference.service;

/**
 * Custom exception for search-related errors.
 * Provides structured error information for proper error handling.
 */
public class GlobalSearchException extends RuntimeException {

    private final ErrorType errorType;
    private final String searchTerm;

    public GlobalSearchException(ErrorType errorType, String searchTerm) {
        super(buildMessage(errorType, searchTerm));
        this.errorType = errorType;
        this.searchTerm = searchTerm;
    }

    public GlobalSearchException(ErrorType errorType, String searchTerm, Throwable cause) {
        super(buildMessage(errorType, searchTerm), cause);
        this.errorType = errorType;
        this.searchTerm = searchTerm;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public int getHttpStatus() {
        return errorType.getHttpStatus();
    }

    private static String buildMessage(ErrorType errorType, String searchTerm) {
        return String.format("%s: %s [term: %s]",
                errorType.name(),
                errorType.getDescription(),
                searchTerm != null ? searchTerm : "N/A");
    }

    /**
     * Error types for search operations.
     */
    public enum ErrorType {
        INVALID_INPUT(400, "Invalid search input"),
        TIMEOUT(408, "Search operation timed out"),
        RATE_LIMITED(429, "Too many requests, please slow down"),
        DATABASE_ERROR(500, "Database error occurred"),
        THREAD_POOL_SHUTDOWN(503, "Search service is shutting down"),
        THREAD_POOL_EXHAUSTED(503, "Search service is temporarily unavailable"),
        THREAD_INTERRUPTED(500, "Search operation was interrupted"),
        EXECUTION_FAILED(500, "Search execution failed"),
        NOT_FOUND(404, "Resource not found"),
        UNAUTHORIZED(401, "Not authorized to perform this search"),
        FORBIDDEN(403, "Access to this resource is forbidden");

        private final int httpStatus;
        private final String description;

        ErrorType(int httpStatus, String description) {
            this.httpStatus = httpStatus;
            this.description = description;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public String getDescription() {
            return description;
        }
    }
}