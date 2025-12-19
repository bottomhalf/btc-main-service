package bt.conference.controller;

import bt.conference.model.GlobalSearchResponse;
import bt.conference.service.GlobalSearchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/search/")
public class GlobalSearchController {

    private static final Logger logger = LoggerFactory.getLogger(GlobalSearchController.class);

    private final GlobalSearchService searchService;

    @Autowired
    public GlobalSearchController(GlobalSearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Health check
     * GET /api/search/health
     */
    @GetMapping("health/")
    public ResponseEntity<Map<String, Object>> health() {
        boolean healthy = searchService.isHealthy();
        return ResponseEntity
                .status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", healthy ? "UP" : "DOWN",
                        "service", "GlobalSearchService"
                ));
    }

    /**
     * Metrics for monitoring
     * GET /api/search/metrics
     */
    @GetMapping("metrics/")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(searchService.getMetrics());
    }

    /**
     * Typeahead search - use as user types (debounce on frontend!)
     * GET /api/search/typeahead?q=ist
     * <p>
     * Returns limited results (5 per category) for quick display
     */
    @GetMapping("/typeahead")
    public ResponseEntity<GlobalSearchResponse> typeahead(
            @RequestParam("q") String query,
            @RequestParam("fs") String fullSearch
    ) {
        GlobalSearchResponse response = searchService.typeahead(query, fullSearch);
        return buildResponse(response);
    }

    /**
     * Full global search with pagination
     * GET /api/search/global?q=istiy&page=0&limit=20
     */
    @GetMapping("/global")
    public ResponseEntity<GlobalSearchResponse> globalSearch(
            @RequestParam("q") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            Principal principal) {

        String userId = principal != null ? principal.getName() : null;
        logger.debug("Global search: query='{}', page={}, limit={}, user='{}'", query, page, limit, userId);

        GlobalSearchResponse response = searchService.search(query, userId, page, limit);
        return buildResponse(response);
    }

    /**
     * Search only users/people
     * GET /api/search/users?q=istiy&page=0&limit=20
     */
    @GetMapping("/users")
    public ResponseEntity<GlobalSearchResponse> searchUsers(
            @RequestParam("q") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            Principal principal) {

        String userId = principal != null ? principal.getName() : null;
        GlobalSearchResponse response = searchService.searchUsers(query, userId, page, limit);
        return buildResponse(response);
    }

    /**
     * Search only conversations/chats
     * GET /api/search/conversations?q=istiy&page=0&limit=20
     */
    @GetMapping("/conversations")
    public ResponseEntity<GlobalSearchResponse> searchConversations(
            @RequestParam("q") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            Principal principal) {

        String userId = principal != null ? principal.getName() : null;
        GlobalSearchResponse response = searchService.searchConversations(query, userId, page, limit);
        return buildResponse(response);
    }

    /**
     * Build appropriate response based on error state
     */
    private ResponseEntity<GlobalSearchResponse> buildResponse(GlobalSearchResponse response) {
        if (response.hasError()) {
            HttpStatus status = switch (response.getError().getCode()) {
                case "INVALID_INPUT" -> HttpStatus.BAD_REQUEST;
                case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
                case "TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
                case "THREAD_POOL_EXHAUSTED", "THREAD_POOL_SHUTDOWN" -> HttpStatus.SERVICE_UNAVAILABLE;
                case "UNAUTHORIZED" -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            return ResponseEntity.status(status).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Global exception handler for this controller
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalSearchResponse> handleException(Exception ex) {
        logger.error("Unhandled exception in search controller: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(GlobalSearchResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}