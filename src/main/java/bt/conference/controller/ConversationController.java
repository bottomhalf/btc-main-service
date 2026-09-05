package bt.conference.controller;

import bt.conference.dto.*;
import bt.conference.entity.Conversation;
import bt.conference.entity.ConversationMembers;
import bt.conference.entity.Message;
import bt.conference.model.CreateGroupRequest;
import bt.conference.service.ConversationService;
import com.fierhub.model.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = {"api/conversations/", "api/chat/"})
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;

    /**
     * Get ALL conversations with pagination
     * GET /api/conversations?pageNumber=1&pageSize=10
     */
    @GetMapping("get-all")
    public BaseResponse getAllConversations(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Conversation> response = conversationService
                .getAllConversations(pageNumber, pageSize);

        return BaseResponse.Ok(response);
    }

    /**
     * Get ALL conversations with pagination
     * GET /api/conversations?pageNumber=1&pageSize=10
     */
    @GetMapping("rooms")
    public BaseResponse getRooms(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Conversation> response = conversationService
                .getRoomsService(pageNumber, pageSize);

        return BaseResponse.Ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * GET /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search")
    public BaseResponse searchConversations(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Conversation> response = conversationService
                .searchConversations(term, pageNumber, pageSize);

        return BaseResponse.Ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * GET /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @PutMapping("create/{senderId}/{recipientId}")
    public BaseResponse createChannel(@PathVariable("senderId") String senderId, @PathVariable("recipientId") String recipientId) throws Exception {
        Conversation response = conversationService.createSingleChannelService(senderId, recipientId);
        return BaseResponse.Ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * POST /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @PostMapping("build-group/{id}")
    public BaseResponse buildGroupChannel(@PathVariable("id") String id, @RequestBody CreateGroupRequest groupRequest) throws Exception {
        Conversation response = conversationService.createGroupChannelService(id, groupRequest);
        return BaseResponse.Ok(response);
    }

    /**
     * Search conversations by username, email, or conversation name
     * POST /api/conversations/search?term=john&pageNumber=1&pageSize=10
     */
    @PostMapping("create-group/{userId}/{groupName}/{conversationId}")
    public BaseResponse createGroup(@PathVariable("groupName") String groupName,
                                   @PathVariable("userId") String userId,
                                   @PathVariable("conversationId") String conversationId,
                                   @RequestBody List<Conversation.Participant> groupUsers) {
        Conversation response = conversationService.createGroupService(userId, groupName, conversationId, groupUsers);
        return BaseResponse.Ok(response);
    }

    /**
     * Add members to an existing group conversation
     * POST /api/conversations/add-members/{conversationId}?addedBy={userId}
     */
    @PostMapping("add-members/{conversationId}")
    public BaseResponse addMembersToGroup(@PathVariable("conversationId") String conversationId,
                                          @RequestParam("addedBy") String addedBy,
                                          @RequestBody List<String> userIds) {
        Conversation response = conversationService.addMembersToGroupService(conversationId, addedBy, userIds);
        return BaseResponse.Ok(response);
    }

    // =========================================================================
    // Chat Endpoints (get-chat-detail, update-chat-detail, delete-chat, etc.)
    // =========================================================================

    /**
     * Get chat details by chatId (from path variable)
     * GET /api/chat/get-chat-detail/{chatId} or /api/conversations/get-chat-detail/{chatId}
     */
    @GetMapping("get-chat-detail/{chatId}")
    public BaseResponse getChatDetail(@PathVariable("chatId") String chatId) throws Exception {
        ChatDetailResponse response = conversationService.getChatDetailService(chatId);
        return BaseResponse.Ok(response);
    }

    /**
     * Get chat details by chatId (from query parameter)
     * GET /api/chat/get-chat-detail?chatId={chatId}
     */
    @GetMapping("get-chat-detail")
    public BaseResponse getChatDetailByParam(@RequestParam("chatId") String chatId) throws Exception {
        ChatDetailResponse response = conversationService.getChatDetailService(chatId);
        return BaseResponse.Ok(response);
    }

    /**
     * Update chat details by chatId
     * PUT /api/chat/update-chat-detail/{chatId}
     */
    @PutMapping("update-chat-detail/{chatId}")
    public BaseResponse updateChatDetail(
            @PathVariable("chatId") String chatId,
            @RequestBody UpdateChatDetailRequest request
    ) throws Exception {
        ChatDetailResponse response = conversationService.updateChatDetailService(chatId, request);
        return BaseResponse.Ok(response);
    }

    /**
     * Update chat details with request body (PUT)
     * PUT /api/chat/update-chat-detail
     */
    @PutMapping("update-chat-detail")
    public BaseResponse updateChatDetailBody(@RequestBody UpdateChatDetailRequest request) throws Exception {
        ChatDetailResponse response = conversationService.updateChatDetailService(request.getChatId(), request);
        return BaseResponse.Ok(response);
    }

    /**
     * Update chat details with request body (POST)
     * POST /api/chat/update-chat-detail
     */
    @PostMapping("update-chat-detail")
    public BaseResponse updateChatDetailPost(@RequestBody UpdateChatDetailRequest request) throws Exception {
        ChatDetailResponse response = conversationService.updateChatDetailService(request.getChatId(), request);
        return BaseResponse.Ok(response);
    }

    /**
     * Delete chat by chatId (soft delete by default, permanent=true for hard delete)
     * DELETE /api/chat/delete-chat/{chatId}?permanent=false
     */
    @DeleteMapping("delete-chat/{chatId}")
    public BaseResponse deleteChat(
            @PathVariable("chatId") String chatId,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent
    ) throws Exception {
        DeleteChatResponse response = conversationService.deleteChatService(chatId, permanent);
        return BaseResponse.Ok(response);
    }

    /**
     * Delete chat by query parameter
     * DELETE /api/chat/delete-chat?chatId={chatId}&permanent=false
     */
    @DeleteMapping("delete-chat")
    public BaseResponse deleteChatByParam(
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent
    ) throws Exception {
        DeleteChatResponse response = conversationService.deleteChatService(chatId, permanent);
        return BaseResponse.Ok(response);
    }

    /**
     * Delete chat by POST request (for HTTP clients unable to issue DELETE)
     * POST /api/chat/delete-chat/{chatId}?permanent=false
     */
    @PostMapping("delete-chat/{chatId}")
    public BaseResponse deleteChatPost(
            @PathVariable("chatId") String chatId,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent
    ) throws Exception {
        DeleteChatResponse response = conversationService.deleteChatService(chatId, permanent);
        return BaseResponse.Ok(response);
    }

    /**
     * Delete chat by POST request with query parameter
     * POST /api/chat/delete-chat?chatId={chatId}&permanent=false
     */
    @PostMapping("delete-chat")
    public BaseResponse deleteChatPostByParam(
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent
    ) throws Exception {
        DeleteChatResponse response = conversationService.deleteChatService(chatId, permanent);
        return BaseResponse.Ok(response);
    }

    /**
     * Clear chat messages and reactions without removing the conversation
     * POST /api/chat/clear-chat/{chatId}?permanent=false
     */
    @PostMapping("clear-chat/{chatId}")
    public BaseResponse clearChat(
            @PathVariable("chatId") String chatId,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent
    ) throws Exception {
        ClearChatResponse response = conversationService.clearChatService(chatId, permanent);
        return BaseResponse.Ok(response);
    }

    /**
     * Clear chat messages and reactions by query parameter
     * POST /api/chat/clear-chat?chatId={chatId}&permanent=false
     */
    @PostMapping("clear-chat")
    public BaseResponse clearChatByParam(
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent
    ) throws Exception {
        ClearChatResponse response = conversationService.clearChatService(chatId, permanent);
        return BaseResponse.Ok(response);
    }

    /**
     * Get members of a chat
     * GET /api/chat/get-chat-members/{chatId}
     */
    @GetMapping("get-chat-members/{chatId}")
    public BaseResponse getChatMembers(@PathVariable("chatId") String chatId) throws Exception {
        List<ConversationMembers> response = conversationService.getChatMembersService(chatId);
        return BaseResponse.Ok(response);
    }

    /**
     * Get members of a chat by query parameter
     * GET /api/chat/get-chat-members?chatId={chatId}
     */
    @GetMapping("get-chat-members")
    public BaseResponse getChatMembersByParam(@RequestParam("chatId") String chatId) throws Exception {
        List<ConversationMembers> response = conversationService.getChatMembersService(chatId);
        return BaseResponse.Ok(response);
    }

    /**
     * Update individual member status/settings (mute, pin, archive, nickname, role, etc.)
     * PUT /api/chat/update-member-status/{chatId}
     */
    @PutMapping("update-member-status/{chatId}")
    public BaseResponse updateMemberStatus(
            @PathVariable("chatId") String chatId,
            @RequestBody UpdateMemberStatusRequest request
    ) throws Exception {
        ConversationMembers response = conversationService.updateMemberStatusService(chatId, request);
        return BaseResponse.Ok(response);
    }

    /**
     * Update individual member status/settings (POST alternative)
     * POST /api/chat/update-member-status/{chatId}
     */
    @PostMapping("update-member-status/{chatId}")
    public BaseResponse updateMemberStatusPost(
            @PathVariable("chatId") String chatId,
            @RequestBody UpdateMemberStatusRequest request
    ) throws Exception {
        ConversationMembers response = conversationService.updateMemberStatusService(chatId, request);
        return BaseResponse.Ok(response);
    }

    /**
     * Get paginated messages for a chat
     * GET /api/chat/get-chat-messages/{chatId}?pageNumber=1&pageSize=20
     */
    @GetMapping("get-chat-messages/{chatId}")
    public BaseResponse getChatMessages(
            @PathVariable("chatId") String chatId,
            @RequestParam(value = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) throws Exception {
        PagedResponse<Message> response = conversationService.getChatMessagesService(chatId, pageNumber, pageSize);
        return BaseResponse.Ok(response);
    }

    /**
     * Get paginated messages for a chat by query param
     * GET /api/chat/get-chat-messages?chatId={chatId}&pageNumber=1&pageSize=20
     */
    @GetMapping("get-chat-messages")
    public BaseResponse getChatMessagesByParam(
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) throws Exception {
        PagedResponse<Message> response = conversationService.getChatMessagesService(chatId, pageNumber, pageSize);
        return BaseResponse.Ok(response);
    }
}
