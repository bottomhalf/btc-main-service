package bt.conference.controller;

import bt.conference.dto.*;
import bt.conference.entity.Conversation;
import bt.conference.model.GroupUser;
import bt.conference.service.ConversationService;
import in.bottomhalf.common.models.ApiResponse;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse getAllConversations(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Conversation> response = conversationService
                .getAllConversations(pageNumber, pageSize);

        return ApiResponse.Ok(response);
    }

    /**
     * Get ALL conversations with pagination
     * GET /api/conversations?pageNumber=1&pageSize=10
     */
    @GetMapping("rooms")
    public ApiResponse getRooms(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Conversation> response = conversationService
                .getRoomsService(pageNumber, pageSize);

        return ApiResponse.Ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * GET /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search")
    public ApiResponse searchConversations(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Conversation> response = conversationService
                .searchConversations(term, pageNumber, pageSize);

        return ApiResponse.Ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * GET /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @PostMapping("create/{id}")
    public ApiResponse createChannel(@PathVariable("id") String id, @RequestBody Conversation conversation) {
        Conversation response = conversationService.createSingleChannelService(id, conversation);
        return ApiResponse.Ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * POST /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @PostMapping("build-group/{id}")
    public ApiResponse buildGroupChannel(@PathVariable("id") String id, @RequestBody Conversation conversation) {
        Conversation response = conversationService.createGroupChannelService(id, conversation);
        return ApiResponse.Ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * POST /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @PostMapping("create-group/{userId}/{groupName}")
    public ApiResponse createGroup(@PathVariable("groupName") String groupName,
                                   @PathVariable("userId") String userId,
                                   @RequestBody List<Conversation.Participant> groupUsers) {
        Conversation response = conversationService.createGroupService(userId, groupName, groupUsers);
        return ApiResponse.Ok(response);
    }
}
