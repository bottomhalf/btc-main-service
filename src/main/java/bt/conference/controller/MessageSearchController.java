package bt.conference.controller;

import bt.conference.model.GlobalSearchResponse;
import bt.conference.model.MessageSearchResult;
import bt.conference.repository.MessageSearchRepository;
import bt.conference.service.MessageSearchService;
import in.bottomhalf.common.models.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages/")
public class MessageSearchController {

    private static final Logger logger = LoggerFactory.getLogger(MessageSearchController.class);

    private final MessageSearchService messageSearchService;

    @Autowired
    public MessageSearchController(MessageSearchService messageSearchService) {
        this.messageSearchService = messageSearchService;
    }

    /**
     * Search only conversations/chats
     * GET /api/search/conversations?q=istiy&page=0&limit=20
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse> searchConversations(
            @RequestParam("q") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        MessageSearchResult response = messageSearchService.searchMessagesService(query, page, limit);
        return ResponseEntity.ok(ApiResponse.Ok(response));
    }

    /**
     * Search only conversations/chats
     * GET /api/search/messages?id=0000-0000-000-00000&page=0&limit=20
     */
    @GetMapping("get")
    public ResponseEntity<ApiResponse> getMessages(
            @RequestParam("id") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        MessageSearchResult response = messageSearchService.searchMessagesService(query, page, limit);
        return ResponseEntity.ok(ApiResponse.Ok(response));
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