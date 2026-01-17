package bt.conference.service;

import bt.conference.dto.*;
import bt.conference.entity.Conversation;
import bt.conference.entity.Conversation.Participant;
import bt.conference.entity.UserCache;
import bt.conference.repository.ConversationRepository;
import bt.conference.repository.UserCacheRepository;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fierhub.model.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.logging.Logger;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final UserCacheRepository userCacheRepository;
    private final MongoTemplate mongoTemplate;
    private final UserSession userSession;

    private static final Logger logger = Logger.getLogger(ObjectIdGenerators.UUIDGenerator.class.getName());

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
     * Get ALL conversations that contains current user id with pagination (No filter)
     */
    public PagedResponse<Conversation> getRoomsService(
            int pageNumber,
            int pageSize) {
        int skip = (pageNumber - 1) * pageSize;

        // Build criteria dynamically
        Criteria criteria = Criteria.where("participant_ids").is(this.userSession.getUserId())
                .and("is_active").is(true);

        // Create query
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "last_message_at"))
                .skip(skip)
                .limit(pageSize);

        // Get total count for pagination
        long total = mongoTemplate.count(query, Conversation.class);

        List<Conversation> conversations = mongoTemplate.find(query, Conversation.class);

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return PagedResponse.of(
                conversations,
                totalPages,
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
    public PagedResponse<Conversation> searchConversationsRecentGroup(
            String searchTerm,
            int pageNumber,
            int pageSize
    ) {
        int skip = (pageNumber - 1) * pageSize;

        Query query = new Query();

        // Add search filter if term provided
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String pattern = searchTerm.trim();

            Criteria searchCriteria = Criteria
                    .where("conversation_type").is("group")
                    .and("participants.user_id").regex(pattern, "i");

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
                    Criteria.where("participants.user_id").regex(pattern, "i"),
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
    public Conversation createSingleChannelService(String senderId, Conversation conversation) {
        // Validate: Check only two participants for direct chat
        if (conversation.getParticipantIds().size() != 2) {
            throw new IllegalArgumentException("Cannot create conversation, required sender and receiver detail");
        }

        return createConversationService(senderId, "direct", conversation);
    }

    /**
     * Search conversations by term (username, email, conversation_name)
     */
    public Conversation createGroupChannelService(String senderId, Conversation conversation) {
        return createConversationService(senderId, "group", conversation);
    }

    private void validateGroupParticipants(List<Participant> participants) {
        if (participants == null || participants.size() < 2) {
            throw new IllegalArgumentException("At least two participants are required to create a group");
        }

        participants.stream().filter(x -> x.getUserId() == null || x.getUserId().isEmpty()).findFirst()
                .ifPresent(x -> {
                    throw new IllegalArgumentException("Participant userId should not be empty or null");
                });
    }

    public Conversation createGroupService(String userId, String groupName, String conversationId, List<Participant> participants) {
        if (groupName == null || groupName.isEmpty()) {
            throw new IllegalArgumentException("Group name should not be empty or null");
        }

        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User id should not be empty or null");
        }


        Optional<UserCache> userCache = this.userCacheRepository.findByUserId(userSession.getUserId());
        var currentUser = userCache.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userSession.getUserId()));

        Instant now = Instant.now();
        participants.add(Participant.builder()
                .userId(currentUser.getUserId())
                .firstName(currentUser.getFirstName())
                .lastName(currentUser.getLastName())
                .email(currentUser.getEmail())
                .avatar(currentUser.getAvatar())
                .joinedAt(now)
                .role("User")
                .build());

        validateGroupParticipants(participants);

        var owner = participants.stream().filter(x -> x.getUserId().equals(userId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("At least one admin is required in group"));

        owner.setRole("admin");

        // Build conversation

        Conversation conversationInstance = Conversation.builder()
                .conversationType("group")
                .participantIds(participants.stream().map(Participant::getUserId).toList())
                .participants(participants)
                .conversationName(groupName)  // Direct chats don't have name
                .conversationAvatar(null)
                .createdBy(owner.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .lastMessage(null)
                .lastMessageAt(now)
                .isActive(true)
                .settings(Conversation.ConversationSettings.builder()
                        .allowReactions(true)
                        .allowPinning(true)
                        .adminOnlyPost(false)
                        .build())
                .build();

        if (isValidObjectIdHex(conversationId)) {
            conversationInstance.setId(conversationId);
        } else {
            var first = participants.stream().findAny();
            if (first.isPresent()) {
                conversationInstance.setId(generateMongoObjectId(userId, first.get().getUserId()));
            } else {
                conversationInstance.setId(generateMongoObjectId(userId, "empty"));
            }
        }

        // Save to database
        Conversation saved = conversationRepository.save(conversationInstance);

        log.info("Created new direct conversation: {}", saved.getId());

        return saved;
    }

    public static boolean isValidObjectIdHex(String hex) {
        if (hex == null || hex.isBlank()) {
            return false;
        }

        return hex.matches("^[a-fA-F0-9]{24}$");
    }

    public String generateUUID(String firstUserId, String secondUserId) {
        // 1️⃣ Sort both IDs lexicographically (FULL string, not first char)
        String id1;
        String id2;

        if (firstUserId.compareTo(secondUserId) <= 0) {
            id1 = firstUserId;
            id2 = secondUserId;
        } else {
            id1 = secondUserId;
            id2 = firstUserId;
        }

        // 2️⃣ Concatenate after sorting
        String value = id1 + id2;

        // 3️⃣ Fixed namespace (same as Go)
        UUID namespace = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

        // 4️⃣ Deterministic UUID (SHA-1 based)
        byte[] nameBytes = (namespace.toString() + value)
                .getBytes(StandardCharsets.UTF_8);

        UUID clientID = UUID.nameUUIDFromBytes(nameBytes);

        logger.info(clientID.toString());

        return clientID.toString();
    }

    public String generateMongoObjectId(String firstUserId, String secondUserId) {
        try {
            // Step 1: Generate deterministic UUID
            String uuid = generateUUID(firstUserId, secondUserId);

            // Step 2: SHA-1 hash of UUID (same family as UUID v5)
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(uuid.getBytes(StandardCharsets.UTF_8));

            // Step 3: Take first 12 bytes → MongoDB ObjectId
            byte[] objectIdBytes = new byte[12];
            System.arraycopy(hash, 0, objectIdBytes, 0, 12);

            return new ObjectId(objectIdBytes).toHexString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate MongoDB ObjectId", e);
        }
    }

    private Conversation createConversationService(String senderId, String type, Conversation conversation) {
        String receiverId;
        Optional<String> filterSender = conversation.getParticipantIds()
                .stream()
                .filter(x -> x.equals(senderId))
                .findFirst();

        if (filterSender.isEmpty()) {
            throw new IllegalArgumentException("Sender id not found in participantIds");
        }

        Optional<String> filterReceiver = conversation.getParticipantIds()
                .stream()
                .filter(x -> !x.equals(senderId))
                .findFirst();

        if (filterReceiver.isEmpty()) {
            throw new IllegalArgumentException("Receiver id not found in participantIds");
        }

        receiverId = filterReceiver.get();

        // Get user details
        UserCache sender = userCacheRepository.findByUserId(senderId)
                .orElseThrow(() -> new RuntimeException("Current user not found: " + senderId));

        UserCache receiver = userCacheRepository.findByUserId(receiverId)
                .orElseThrow(() -> new RuntimeException("Other user not found: " + receiverId));

        // Validate
        if (sender.getUserId().equals(receiver.getUserId())) {
            throw new IllegalArgumentException("Cannot create conversation with yourself");
        }

        log.info("Creating direct conversation between {} and {}", sender.getUserId(), receiver.getUserId());


        // Check if direct conversation already exists
        Optional<Conversation> existing = conversationRepository
                .findDirectConversation(senderId, receiver.getUserId());

        if (existing.isPresent()) {
            log.info("Direct conversation already exists: {}", existing.get().getId());
            return existing.get();
        }

        // Create participants
        List<Participant> participants = new ArrayList<>();
        participants.add(createParticipant(sender, "member"));
        participants.add(createParticipant(receiver, "member"));

        // Create participant IDs list
        List<String> participantIds = List.of(senderId, receiver.getUserId());

        // Build conversation
        Instant now = Instant.now();

        Conversation conversationInstance = Conversation.builder()
                .id(generateMongoObjectId(sender.getUserId(), receiver.getUserId()))
                .conversationType(type)
                .participantIds(participantIds)
                .participants(participants)
                .conversationName(receiver.getUsername())  // Direct chats don't have name
                .conversationAvatar(null)
                .createdBy(senderId)
                .createdAt(now)
                .updatedAt(now)
                .lastMessage(null)
                .lastMessageAt(now)
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
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .joinedAt(Instant.now())
                .role(role)
                .build();
    }
}
