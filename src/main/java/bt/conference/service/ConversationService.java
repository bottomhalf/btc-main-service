package bt.conference.service;

import bt.conference.dto.*;
import bt.conference.entity.Conversation;
import bt.conference.entity.Conversation.Participant;
import bt.conference.entity.UserCache;
import bt.conference.repository.ConversationRepository;
import bt.conference.repository.UserCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final UserCacheRepository userCacheRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Get ALL conversations with pagination (No filter)
     */
    public PagedResponse<Conversation> getAllConversations(int pageNumber, int pageSize) {

        Pageable pageable = PageRequest.of(
                pageNumber - 1,  // Spring uses 0-indexed pages
                pageSize,
                Sort.by(Sort.Direction.DESC, "last_message_at")
        );

        Page<Conversation> page = conversationRepository.findAll(pageable);

        return PagedResponse.of(
                page.getContent(),
                page.getTotalPages(),
                pageNumber,
                pageSize
        );
    }

    /**
     * Get ALL conversations using MongoTemplate (Alternative)
     */
    public PagedResponse<Conversation> getAllConversationsWithTemplate(int pageNumber, int pageSize) {

        int skip = (pageNumber - 1) * pageSize;

        // Count total
        long totalRecords = mongoTemplate.count(new Query(), Conversation.class);

        // Fetch with pagination
        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "last_message_at"))
                .skip(skip)
                .limit(pageSize);

        List<Conversation> conversations = mongoTemplate.find(query, Conversation.class);

        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

        return PagedResponse.of(
                conversations,
                totalPages,
                pageNumber,
                pageSize
        );
    }

    /**
     * Search conversations by term (username, email, conversation_name)
     */
    public PagedResponse<Conversation> searchConversations(
            String searchTerm,
            int pageNumber,
            int pageSize
    ) {
        int skip = (pageNumber - 1) * pageSize;

        Query query = new Query();

        // Add search filter if term provided
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String pattern = searchTerm.trim();

            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("conversation_name").regex(pattern, "i"),
                    Criteria.where("participants.username").regex(pattern, "i"),
                    Criteria.where("participants.email").regex(pattern, "i")
            );

            query.addCriteria(searchCriteria);
        }

        // Count total matching records
        long totalRecords = mongoTemplate.count(query, Conversation.class);

        log.info("Search term: '{}', Total records found: {}", searchTerm, totalRecords);

        // Add sorting and pagination
        query.with(Sort.by(Sort.Direction.DESC, "last_message_at"));
        query.skip(skip);
        query.limit(pageSize);

        // Execute query
        List<Conversation> conversations = mongoTemplate.find(query, Conversation.class);

        log.info("Returning {} conversations", conversations.size());

        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

        return PagedResponse.of(
                conversations,
                totalPages,
                pageNumber,
                pageSize
        );
    }

    /**
     * Search conversations by term (username, email, conversation_name)
     */
    public Conversation createChannelService(Conversation conversation) {
        // Validate
        if (conversation.getParticipantIds().size() == 2
                && conversation.getParticipantIds().get(0).equals(conversation.getParticipantIds().get(1))) {
            throw new IllegalArgumentException("Cannot create conversation with yourself");
        }

        String senderId = conversation.getParticipantIds().get(0);
        String receiverId = conversation.getParticipantIds().get(1);

        log.info("Creating direct conversation between {} and {}", senderId, receiverId);


        // Check if direct conversation already exists
        Optional<Conversation> existing = conversationRepository
                .findDirectConversation(senderId, receiverId);

        if (existing.isPresent()) {
            log.info("Direct conversation already exists: {}", existing.get().getId());
            return existing.get();
        }

        // Get user details
        UserCache currentUser = userCacheRepository.findByUserId(senderId)
                .orElseThrow(() -> new RuntimeException("Current user not found: " + senderId));

        UserCache otherUser = userCacheRepository.findByUserId(receiverId)
                .orElseThrow(() -> new RuntimeException("Other user not found: " + receiverId));

        // Create participants
        List<Conversation.Participant> participants = new ArrayList<>();
        participants.add(createParticipant(currentUser, "member"));
        participants.add(createParticipant(otherUser, "member"));

        // Create participant IDs list
        List<String> participantIds = List.of(senderId, receiverId);

        // Build conversation
        Instant now = Instant.now();

        Conversation conversationInstance = Conversation.builder()
                .conversationType("direct")
                .participantIds(participantIds)
                .participants(participants)
                .conversationName(null)  // Direct chats don't have name
                .conversationAvatar(null)
                .createdBy(senderId)
                .createdAt(now)
                .updatedAt(now)
                .lastMessage(null)
                .lastMessageAt(null)
                .isActive(true)
                .settings(Conversation.ConversationSettings.builder()
                        .allowReactions(true)
                        .allowPinning(true)
                        .adminOnlyPost(false)
                        .build())
                .build();

        // Save to database
        Conversation saved = conversationRepository.save(conversationInstance);

        log.info("Created new direct conversation: {}", saved.getId());

        return saved;
    }

    public void updateLastMessage(String conversationId, Conversation.LastMessage lastMessage) {
        return;
    }

    // ==================== HELPER METHODS ====================

    private Participant createParticipant(UserCache user, String role) {
        return Participant.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .joinedAt(Instant.now())
                .role(role)
                .build();
    }
}
