package bt.conference.controller;

import bt.conference.dto.*;
import bt.conference.entity.Conversation;
import bt.conference.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "api/conversations/")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;

    /**
     * Get ALL conversations with pagination
     * GET /api/conversations?pageNumber=1&pageSize=10
     */
    @GetMapping("get-all")
    public ResponseEntity<PagedResponse<Conversation>> getAllConversations(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Conversation> response = conversationService
                .getAllConversations(pageNumber, pageSize);

        return ResponseEntity.ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * GET /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search")
    public ResponseEntity<PagedResponse<Conversation>> searchConversations(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Conversation> response = conversationService
                .searchConversations(term, pageNumber, pageSize);

        return ResponseEntity.ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * GET /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @PostMapping("create-channel")
    public ResponseEntity<Conversation> createChannel(@RequestBody Conversation conversation) {
        Conversation response = conversationService.createChannelService(conversation);
        return ResponseEntity.ok(response);
    }
}
