package bt.conference.controller;

import bt.conference.model.GlobalSearchResponse;
import bt.conference.service.GlobalSearchService;

import in.bottomhalf.common.models.ApiErrorResponse;
import in.bottomhalf.common.models.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ApiResponse health() {
        boolean healthy = searchService.isHealthy();
        if (healthy) {
            return ApiResponse.Ok(Map.of(
                    "status", "UP",
                    "service", "GlobalSearchService"
            ));
        }
        return ApiErrorResponse.BadRequest(Map.of(
                "status", "DOWN",
                "service", "GlobalSearchService"
        ));
    }

    /**
     * Metrics for monitoring
     * GET /api/search/metrics
     */
    @GetMapping("metrics/")
    public ApiResponse metrics() {
        return ApiResponse.Ok(searchService.getMetrics());
    }

    /**
     * Typeahead search - use as user types (debounce on frontend!)
     * GET /api/search/typeahead?q=ist
     * <p>
     * Returns limited results (5 per category) for quick display
     */
    @GetMapping("/typeahead")
    public ApiResponse typeahead(
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
    public ApiResponse globalSearch(
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
    public ApiResponse searchUsers(
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
    public ApiResponse searchConversations(
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
    private ApiResponse buildResponse(GlobalSearchResponse response) {
        if (response.hasError()) {
            return ApiErrorResponse.BadRequest(response);
        }
        return ApiResponse.Ok(response);
    }

    /**
     * Global exception handler for this controller
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse handleException(Exception ex) {
        logger.error("Unhandled exception in search controller: {}", ex.getMessage(), ex);
        return ApiErrorResponse.BadRequest(GlobalSearchResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}