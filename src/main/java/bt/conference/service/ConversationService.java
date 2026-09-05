package bt.conference.service;

import bt.conference.dto.*;
import bt.conference.entity.Conversation;
import bt.conference.entity.Conversation.Participant;
import bt.conference.entity.ConversationMembers;
import bt.conference.entity.Users;
import bt.conference.entity.Message;
import bt.conference.entity.Reaction;
import bt.conference.model.ApplicationConstant;
import bt.conference.model.CreateGroupRequest;
import bt.conference.repository.ConversationMembersRepository;
import bt.conference.repository.ConversationRepository;
import bt.conference.repository.MessageRepository;
import bt.conference.repository.ReactionRepository;
import bt.conference.repository.UsersRepository;
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
import org.springframework.data.mongodb.core.query.Update;
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
    private final ConversationMembersRepository conversationMembersRepository;
    private final MessageRepository messageRepository;
    private final ReactionRepository reactionRepository;
    private final UsersRepository usersRepository;
    private final MongoTemplate mongoTemplate;
    private final UserSession userSession;

    private static final String COLLECTION_CONVERSATIONS = "conversations";
    private static final String COLLECTION_CONVERSATION_MEMBERS = "conversation_members";
    private static final String COLLECTION_MESSAGES = "messages";
    private static final String COLLECTION_REACTIONS = "reactions";

    private static final Logger logger = Logger.getLogger(ObjectIdGenerators.UUIDGenerator.class.getName());

    /**
     * Get ALL conversations with pagination (No filter)
     */
    public PagedResponse<Conversation> getAllConversations(int pageNumber, int pageSize) {

        Pageable pageable = PageRequest.of(
                pageNumber - 1,  // Spring uses 0-indexed pages
                pageSize,
                Sort.by(Sort.Direction.DESC, "lastMessageAt")
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
        
        List<ConversationMembers> memberships = conversationMembersRepository.findByUserId(this.userSession.getUserId());
        List<String> conversationIds = memberships.stream()
                .map(ConversationMembers::getConversationId)
                .toList();

        if (conversationIds.isEmpty()) {
            return PagedResponse.of(
                    Collections.emptyList(),
                    0,
                    pageNumber,
                    pageSize
            );
        }

        Pageable pageable = PageRequest.of(
                pageNumber - 1,
                pageSize
        );

        Page<Conversation> page = conversationRepository.findByIdInAndIsDeletedFalseOrderByLastMessageAtDesc(conversationIds, pageable);

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
                .with(Sort.by(Sort.Direction.DESC, "lastMessageAt"))
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
        query.addCriteria(Criteria.where("type").is("GROUP"));
        query.addCriteria(Criteria.where("isDeleted").is(false));

        // Add search filter if term provided
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String pattern = searchTerm.trim();

            // Find users matching search term
            Query userQuery = new Query(new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i")
            ));
            List<Users> users = mongoTemplate.find(userQuery, Users.class);
            List<String> userIds = users.stream().map(Users::getId).toList();

            // Find memberships
            List<ConversationMembers> memberships = mongoTemplate.find(
                    new Query(Criteria.where("userId").in(userIds)),
                    ConversationMembers.class
            );
            List<String> conversationIds = memberships.stream()
                    .map(ConversationMembers::getConversationId)
                    .toList();

            query.addCriteria(Criteria.where("id").in(conversationIds));
        }

        // Count total matching records
        long totalRecords = mongoTemplate.count(query, Conversation.class);

        log.info("Search term: '{}', Total records found: {}", searchTerm, totalRecords);

        // Add sorting and pagination
        query.with(Sort.by(Sort.Direction.DESC, "lastMessageAt"));
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
        query.addCriteria(Criteria.where("isDeleted").is(false));

        // Add search filter if term provided
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String pattern = searchTerm.trim();

            // Find users matching search term
            Query userQuery = new Query(new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i")
            ));
            List<Users> users = mongoTemplate.find(userQuery, Users.class);
            List<String> userIds = users.stream().map(Users::getId).toList();

            // Find memberships
            List<ConversationMembers> memberships = mongoTemplate.find(
                    new Query(Criteria.where("userId").in(userIds)),
                    ConversationMembers.class
            );
            List<String> conversationIds = memberships.stream()
                    .map(ConversationMembers::getConversationId)
                    .toList();

            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("title").regex(pattern, "i"),
                    Criteria.where("description").regex(pattern, "i"),
                    Criteria.where("id").in(conversationIds)
            );

            query.addCriteria(searchCriteria);
        }

        // Count total matching records
        long totalRecords = mongoTemplate.count(query, Conversation.class);

        log.info("Search term: '{}', Total records found: {}", searchTerm, totalRecords);

        // Add sorting and pagination
        query.with(Sort.by(Sort.Direction.DESC, "lastMessageAt"));
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
    public Conversation createSingleChannelService(String senderId, String receiverId) throws Exception {
        // Validate: Check only two participants for direct chat
        if (senderId == null || senderId.isEmpty()) {
            throw new IllegalArgumentException("Cannot create conversation, required sender and receiver detail");
        }

        // Validate: Check only two participants for direct chat
        if (receiverId == null || receiverId.isEmpty()) {
            throw new IllegalArgumentException("Cannot create conversation, required sender and receiver detail");
        }

        return createConversationService(senderId, ApplicationConstant.DirectChat, CreateGroupRequest.builder()
                .memberIds(List.of(senderId, receiverId))
                .build());
    }

    /**
     * Search conversations by term (username, email, conversation_name)
     */
    public Conversation createGroupChannelService(String senderId, CreateGroupRequest groupRequest) throws Exception {
        return createConversationService(senderId, ApplicationConstant.GroupChat, groupRequest);
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


        Optional<Users> userCache = this.usersRepository.findById(userSession.getUserId());
        var currentUser = userCache.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userSession.getUserId()));

        Instant now = Instant.now();
        participants.add(Participant.builder()
                .userId(currentUser.getId())
                .firstName(currentUser.getFirstName())
                .lastName(currentUser.getLastName())
                .email(currentUser.getEmail())
                .avatar(currentUser.getAvatarUrl())
                .joinedAt(now)
                .role("User")
                .build());

        validateGroupParticipants(participants);

        var owner = participants.stream().filter(x -> x.getUserId().equals(userId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("At least one admin is required in group"));

        owner.setRole("admin");

        // Build conversation
        Conversation conversationInstance = Conversation.builder()
                .type("GROUP")
                .title(groupName)
                .avatar(null)
                .createdBy(owner.getUserId())
                .createdAt(now)
                .lastMessageAt(now)
                .isDeleted(false)
                .memberCount(participants.size())
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

        // Save members in conversation_members
        for (Participant p : participants) {
            ConversationMembers member = ConversationMembers.builder()
                    .id(new ObjectId().toHexString())
                    .conversationId(saved.getId())
                    .userId(p.getUserId())
                    .role("admin".equalsIgnoreCase(p.getRole()) ? "ADMIN" : "MEMBER")
                    .joinedAt(now)
                    .joinedBy(owner.getUserId())
                    .status("ACTIVE")
                    .unreadCount(0)
                    .isMuted(false)
                    .isPinned(false)
                    .isArchived(false)
                    .notification("ALL")
                    .nickname(p.getFirstName() + "_" + p.getLastName())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            conversationMembersRepository.save(member);
        }

        log.info("Created new group conversation: {}", saved.getId());

        return saved;
    }

    public static boolean isValidObjectIdHex(String hex) {
        if (hex == null || hex.isBlank()) {
            return false;
        }

        return hex.matches("^[a-fA-F0-9]{24}$");
    }

    public String generateUUID(String firstUserId, String secondUserId) {
        // Sort both IDs lexicographically
        String id1;
        String id2;

        if (firstUserId.compareTo(secondUserId) <= 0) {
            id1 = firstUserId;
            id2 = secondUserId;
        } else {
            id1 = secondUserId;
            id2 = firstUserId;
        }

        // Concatenate after sorting
        String value = id1 + id2;

        // Fixed namespace
        UUID namespace = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

        // Deterministic UUID
        byte[] nameBytes = (namespace.toString() + value)
                .getBytes(StandardCharsets.UTF_8);

        UUID clientID = UUID.nameUUIDFromBytes(nameBytes);

        logger.info(clientID.toString());

        return clientID.toString();
    }

    public String generateMongoObjectId(String firstUserId, String secondUserId) {
        try {
            // Generate deterministic UUID
            String uuid = generateUUID(firstUserId, secondUserId);

            // SHA-1 hash of UUID
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(uuid.getBytes(StandardCharsets.UTF_8));

            // Take first 12 bytes
            byte[] objectIdBytes = new byte[12];
            System.arraycopy(hash, 0, objectIdBytes, 0, 12);

            return new ObjectId(objectIdBytes).toHexString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate MongoDB ObjectId", e);
        }
    }

    private Conversation createConversationService(String senderId, String type, CreateGroupRequest groupRequest) throws Exception {
        // Get user details
        var users = usersRepository.findAllById(groupRequest.getMemberIds());
        if (users.isEmpty())
            throw  new RuntimeException("Current user not found: " + senderId);

        // Validate
        var senderDetail = users.stream().filter(x -> Objects.equals(x.getId(), senderId)).toList();
        if (senderDetail.size() > 1)
            throw new IllegalArgumentException("Cannot create conversation with yourself");

        var sender = senderDetail.get(0);
        var receivers = users.stream().filter(x -> !x.getId().equals(senderId)).toList();
        if (receivers.isEmpty())
            throw new Exception("Receiver user not found");

        var firstReceiver = receivers.get(0);

        // Check if direct conversation already exists
        if (type.equals(ApplicationConstant.DirectChat)) {
            Optional<Conversation> existing = conversationRepository
                    .findDirectConversation(sender.getId(), firstReceiver.getId());
            if (existing.isPresent()) {
                log.info("Direct conversation already exists: {}", existing.get().getId());
                return existing.get();
            }
        }
        
        // Build conversation
        Instant now = Instant.now();
        String conversationIdHex = generateMongoObjectId(sender.getId(), firstReceiver.getId());
        var title = "";
        if (type.equalsIgnoreCase(ApplicationConstant.DirectChat)) {
            title = receivers.get(0).getFirstName() + " " + firstReceiver.getLastName();
        } else  {
            if (groupRequest.getGroupName() != null && !groupRequest.getGroupName().isEmpty()) {
                title = groupRequest.getGroupName();
            } else {
                title = firstReceiver.getFirstName() + " " + sender.getFirstName();
            }
        }

        Conversation conversationInstance = Conversation.builder()
                .id(conversationIdHex)
                .type(type)
                .title(title)  // Direct chats don't have title
                .avatar(ApplicationConstant.EmptyString)
                .createdBy(senderId)
                .description(ApplicationConstant.EmptyString)
                .createdAt(now)
                .lastMessageId(ApplicationConstant.EmptyString)
                .lastMessageAt(now)
                .isDeleted(false)
                .memberCount(groupRequest.getMemberIds().size())
                .participantIds(new ArrayList<>())
                .searchableMemberInfo(new ArrayList<>())
                .settings(Conversation.ConversationSettings.builder()
                        .allowReactions(true)
                        .allowPinning(true)
                        .adminOnlyPost(false)
                        .build())
                .build();

        for(var user : users) {
            conversationInstance.getParticipantIds().add(user.getId());
            conversationInstance.getSearchableMemberInfo()
                    .add(user.getFirstName() + " " + user.getLastName() + " " + user.getEmail() + " " + user.getId());
        }

        // Save to database
        Conversation saved = conversationRepository.save(conversationInstance);

        // Save members in conversation_members
        List<ConversationMembers> conversationMembers = new ArrayList<>();
        conversationMembers.add(ConversationMembers.builder()
                .id(new ObjectId().toHexString())
                .conversationId(saved.getId())
                .userId(sender.getId())
                .role("ADMIN")
                .joinedAt(now)
                .joinedBy(senderId)
                .status("ACTIVE")
                .unreadCount(0)
                .isMuted(false)
                .isPinned(false)
                .isArchived(false)
                .notification("ALL")
                .nickname(sender.getUsername())
                .createdAt(now)
                .updatedAt(now)
                .build());

        for (var receiver : receivers) {
            conversationMembers.add(ConversationMembers.builder()
                    .id(new ObjectId().toHexString())
                    .conversationId(saved.getId())
                    .userId(receiver.getId())
                    .role("MEMBER")
                    .joinedAt(now)
                    .joinedBy(senderId)
                    .status("ACTIVE")
                    .unreadCount(0)
                    .isMuted(false)
                    .isPinned(false)
                    .isArchived(false)
                    .notification("ALL")
                    .nickname(receiver.getUsername())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        conversationMembersRepository.saveAll(conversationMembers);

        log.info("Created new direct conversation: {}", saved.getId());

        return saved;
    }

    public Conversation createMeetingConversationService(String senderId, String title, List<String> participantsId) throws Exception {
        var senderDetail = usersRepository.findById(senderId).orElseThrow(() -> new Exception("User detail not found"));

        // Build conversation
        Instant now = Instant.now();
        List<String> uniqueParticipantIds = participantsId == null
                ? Collections.emptyList()
                : participantsId.stream()
                .filter(id -> id != null && !id.equals(senderId))
                .distinct()
                .toList();
        List<Users> participants = uniqueParticipantIds.isEmpty()
                ? Collections.emptyList()
                : usersRepository.findAllById(uniqueParticipantIds);

        String conversationIdHex = generateMongoObjectId(senderDetail.getId(), senderDetail.getId());

        Conversation conversationInstance = Conversation.builder()
                .id(conversationIdHex)
                .type(ApplicationConstant.GroupChat)
                .title(title)  // Direct chats don't have title
                .avatar(ApplicationConstant.EmptyString)
                .createdBy(senderId)
                .description(ApplicationConstant.EmptyString)
                .createdAt(now)
                .lastMessageId(ApplicationConstant.EmptyString)
                .lastMessageAt(now)
                .isDeleted(false)
                .participantIds(new ArrayList<>())
                .searchableMemberInfo(new ArrayList<>())
                .settings(Conversation.ConversationSettings.builder()
                        .allowReactions(true)
                        .allowPinning(true)
                        .adminOnlyPost(false)
                        .build())
                .build();

        conversationInstance.getParticipantIds().add(senderId);
        conversationInstance.getSearchableMemberInfo()
                .add(senderDetail.getFirstName() + " " + senderDetail.getLastName() + " " + senderDetail.getEmail() + " " + senderDetail.getId());
        for (var user : participants) {
            conversationInstance.getParticipantIds().add(user.getId());
            conversationInstance.getSearchableMemberInfo()
                    .add(user.getFirstName() + " " + user.getLastName() + " " + user.getEmail() + " " + user.getId());
        }
        conversationInstance.setMemberCount(conversationInstance.getParticipantIds().size() + 1);
        // Save to database
        Conversation saved = conversationRepository.save(conversationInstance);

        // Save members in conversation_members
        List<ConversationMembers> conversationMembers = new ArrayList<>();
        conversationMembers.add(ConversationMembers.builder()
                .id(new ObjectId().toHexString())
                .conversationId(saved.getId())
                .userId(senderDetail.getId())
                .role("ADMIN")
                .joinedAt(now)
                .joinedBy(senderId)
                .status("ACTIVE")
                .unreadCount(0)
                .isMuted(false)
                .isPinned(false)
                .isArchived(false)
                .notification("ALL")
                .nickname(senderDetail.getUsername())
                .createdAt(now)
                .updatedAt(now)
                .build());
            for (var user : participants) {
                conversationMembers.add(ConversationMembers.builder()
                        .id(new ObjectId().toHexString())
                        .conversationId(saved.getId())
                        .userId(user.getId())
                        .role("MEMBER")
                        .joinedAt(now)
                        .joinedBy(senderId)
                        .status("ACTIVE")
                        .unreadCount(0)
                        .isMuted(false)
                        .isPinned(false)
                        .isArchived(false)
                        .notification("ALL")
                        .nickname(user.getUsername())
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
        conversationMembersRepository.saveAll(conversationMembers);

        log.info("Created new meeting conversation: {}", saved.getId());

        return saved;
    }

    public void updateLastMessage(String conversationId, Conversation.LastMessage lastMessage) {
        Optional<Conversation> convOpt = conversationRepository.findById(conversationId);
        if (convOpt.isPresent()) {
            Conversation conv = convOpt.get();
            conv.setLastMessageId(lastMessage.getMessageId());
            conv.setLastMessageAt(lastMessage.getSentAt());
            conversationRepository.save(conv);
        }
    }

    public Conversation addMembersToGroupService(String conversationId, String addedByUserId, List<String> userIds) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!"GROUP".equalsIgnoreCase(conversation.getType())) {
            throw new IllegalArgumentException("Cannot add members to a direct conversation");
        }

        var usersToAdd = usersRepository.findAllById(userIds);
        if (usersToAdd == null || usersToAdd.isEmpty()) {
            throw new IllegalArgumentException("Users not found");
        }

        Instant now = Instant.now();
        List<ConversationMembers> newMembers = new ArrayList<>();
        boolean modified = false;

        for (Users u : usersToAdd) {
            if (!conversation.getParticipantIds().contains(u.getId())) {
                conversation.getParticipantIds().add(u.getId());
                conversation.getSearchableMemberInfo()
                        .add(u.getFirstName() + " " + u.getLastName() + " " + u.getEmail() + " " + u.getId());

                newMembers.add(ConversationMembers.builder()
                        .id(new ObjectId().toHexString())
                        .conversationId(conversation.getId())
                        .userId(u.getId())
                        .role("MEMBER")
                        .joinedAt(now)
                        .joinedBy(addedByUserId)
                        .status("ACTIVE")
                        .unreadCount(0)
                        .isMuted(false)
                        .isPinned(false)
                        .isArchived(false)
                        .notification("ALL")
                        .nickname(u.getFirstName() + "_" + u.getLastName())
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                modified = true;
            }
        }

        if (modified) {
            conversation.setMemberCount(conversation.getParticipantIds().size());
            conversationRepository.save(conversation);
            conversationMembersRepository.saveAll(newMembers);
            log.info("Added {} new members to group {}", newMembers.size(), conversationId);
        }

        return conversation;
    }

    // =========================================================================
    // Chat Operations (MongoDB: conversations, conversation_members, messages, reactions)
    // =========================================================================

    /**
     * Retrieve chat detail by chat_id, including conversation info,
     * conversation_members, message stats, and reactions.
     */
    public ChatDetailResponse getChatDetailService(String chatId) throws Exception {
        validateChatId(chatId);

        Conversation conversation = findConversationById(chatId);
        if (conversation == null) {
            throw new IllegalArgumentException("Chat not found with id: " + chatId);
        }

        // Fetch members from conversation_members collection
        List<ConversationMembers> members = findMembersByChatId(chatId);
        int memberCount = members != null ? members.size() : conversation.getMemberCount();

        // Fetch message stats and recent messages from messages collection
        Query msgQuery = buildChatMessagesQuery(chatId);
        long totalMessages = mongoTemplate.count(msgQuery, Message.class, COLLECTION_MESSAGES);

        Query recentMsgQuery = buildChatMessagesQuery(chatId)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(20);
        List<Message> recentMessages = mongoTemplate.find(recentMsgQuery, Message.class, COLLECTION_MESSAGES);

        // Fetch reactions from reactions collection
        List<String> messageIdentifiers = extractMessageIdentifiers(recentMessages);
        Query reactionQuery = buildChatReactionsQuery(chatId, messageIdentifiers);
        long totalReactions = mongoTemplate.count(reactionQuery, Reaction.class, COLLECTION_REACTIONS);
        List<Reaction> reactions = mongoTemplate.find(reactionQuery.limit(50), Reaction.class, COLLECTION_REACTIONS);

        return ChatDetailResponse.builder()
                .chatId(chatId)
                .conversation(conversation)
                .members(members)
                .memberCount(memberCount)
                .totalMessages(totalMessages)
                .totalReactions(totalReactions)
                .recentMessages(recentMessages)
                .reactions(reactions)
                .build();
    }

    /**
     * Update chat details in MongoDB conversations and conversation_members collection based on chat_id.
     */
    public ChatDetailResponse updateChatDetailService(String chatId, UpdateChatDetailRequest request) throws Exception {
        String targetChatId = (chatId != null && !chatId.trim().isEmpty()) ? chatId.trim() : request.getChatId();
        validateChatId(targetChatId);

        Conversation conversation = findConversationById(targetChatId);
        if (conversation == null) {
            throw new IllegalArgumentException("Chat not found with id: " + targetChatId);
        }

        Query convQuery = buildConversationIdQuery(targetChatId);
        Update convUpdate = new Update();
        boolean convModified = false;

        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            convUpdate.set("title", request.getTitle().trim());
            conversation.setTitle(request.getTitle().trim());
            convModified = true;
        }

        if (request.getDescription() != null) {
            convUpdate.set("description", request.getDescription().trim());
            conversation.setDescription(request.getDescription().trim());
            convModified = true;
        }

        if (request.getAvatar() != null) {
            convUpdate.set("avatar", request.getAvatar().trim());
            conversation.setAvatar(request.getAvatar().trim());
            convModified = true;
        }

        if (request.getType() != null && !request.getType().trim().isEmpty()) {
            convUpdate.set("type", request.getType().trim());
            conversation.setType(request.getType().trim());
            convModified = true;
        }

        if (request.getSettings() != null) {
            convUpdate.set("settings", request.getSettings());
            conversation.setSettings(request.getSettings());
            convModified = true;
        }

        if (convModified) {
            convUpdate.set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(convQuery, convUpdate, Conversation.class, COLLECTION_CONVERSATIONS);
            log.info("Updated conversation {} in MongoDB conversations collection", targetChatId);
        }

        // Apply member updates in conversation_members collection if provided
        if (request.getMemberUpdates() != null && !request.getMemberUpdates().isEmpty()) {
            Instant now = Instant.now();
            for (UpdateChatDetailRequest.MemberUpdateDetail mu : request.getMemberUpdates()) {
                if (mu.getUserId() != null && !mu.getUserId().trim().isEmpty()) {
                    Query mQuery = new Query(new Criteria().andOperator(
                            buildMemberConversationCriteria(targetChatId),
                            Criteria.where("userId").is(mu.getUserId().trim())
                    ));
                    Update mUpdate = new Update();
                    boolean mModified = false;

                    if (mu.getRole() != null) {
                        mUpdate.set("role", mu.getRole().trim());
                        mModified = true;
                    }
                    if (mu.getNickname() != null) {
                        mUpdate.set("nickname", mu.getNickname().trim());
                        mModified = true;
                    }
                    if (mu.getIsMuted() != null) {
                        mUpdate.set("isMuted", mu.getIsMuted());
                        mModified = true;
                    }
                    if (mu.getMuteUntilMillis() != null) {
                        mUpdate.set("muteUntil", Instant.ofEpochMilli(mu.getMuteUntilMillis()));
                        mModified = true;
                    }
                    if (mu.getIsPinned() != null) {
                        mUpdate.set("isPinned", mu.getIsPinned());
                        mModified = true;
                    }
                    if (mu.getIsArchived() != null) {
                        mUpdate.set("isArchived", mu.getIsArchived());
                        mModified = true;
                    }
                    if (mu.getNotification() != null) {
                        mUpdate.set("notification", mu.getNotification().trim());
                        mModified = true;
                    }
                    if (mu.getStatus() != null) {
                        mUpdate.set("status", mu.getStatus().trim());
                        mModified = true;
                    }

                    if (mModified) {
                        mUpdate.set("updatedAt", now);
                        mongoTemplate.updateMulti(mQuery, mUpdate, ConversationMembers.class, COLLECTION_CONVERSATION_MEMBERS);
                        log.info("Updated member {} for chat {} in MongoDB conversation_members collection", mu.getUserId(), targetChatId);
                    }
                }
            }
        }

        return getChatDetailService(targetChatId);
    }

    /**
     * Delete chat by chat_id across MongoDB collections:
     * - conversations
     * - conversation_members
     * - messages
     * - reactions
     *
     * Supports soft delete (default) and permanent (hard) delete.
     */
    public DeleteChatResponse deleteChatService(String chatId, boolean permanent) throws Exception {
        validateChatId(chatId);

        // Fetch message IDs to ensure cascading reaction updates/removals
        Query msgFindQuery = buildChatMessagesQuery(chatId);
        msgFindQuery.fields().include("_id").include("message_id");
        List<Message> messages = mongoTemplate.find(msgFindQuery, Message.class, COLLECTION_MESSAGES);
        List<String> messageIdentifiers = extractMessageIdentifiers(messages);

        Query convQuery = buildConversationIdQuery(chatId);
        Query memberQuery = buildChatMembersQuery(chatId);
        Query msgQuery = buildChatMessagesQuery(chatId);
        Query reactionQuery = buildChatReactionsQuery(chatId, messageIdentifiers);

        long convAffected;
        long membersAffected;
        long messagesAffected;
        long reactionsAffected;

        if (permanent) {
            reactionsAffected = mongoTemplate.remove(reactionQuery, Reaction.class, COLLECTION_REACTIONS).getDeletedCount();
            messagesAffected = mongoTemplate.remove(msgQuery, Message.class, COLLECTION_MESSAGES).getDeletedCount();
            membersAffected = mongoTemplate.remove(memberQuery, ConversationMembers.class, COLLECTION_CONVERSATION_MEMBERS).getDeletedCount();
            convAffected = mongoTemplate.remove(convQuery, Conversation.class, COLLECTION_CONVERSATIONS).getDeletedCount();

            log.info("Permanently deleted chat {}: {} conversations, {} members, {} messages, {} reactions",
                    chatId, convAffected, membersAffected, messagesAffected, reactionsAffected);

            return DeleteChatResponse.builder()
                    .chatId(chatId)
                    .message("Chat permanently deleted from MongoDB collections")
                    .permanent(true)
                    .conversationsAffected(convAffected)
                    .membersAffected(membersAffected)
                    .messagesAffected(messagesAffected)
                    .reactionsAffected(reactionsAffected)
                    .build();
        } else {
            Instant now = Instant.now();

            // 1. Update conversations collection: mark isDeleted = true
            Update convUpdate = new Update()
                    .set("isDeleted", true)
                    .set("updatedAt", now);
            convAffected = mongoTemplate.updateMulti(convQuery, convUpdate, Conversation.class, COLLECTION_CONVERSATIONS).getModifiedCount();

            // 2. Update conversation_members collection: status = DELETED, leftAt = now
            Update memberUpdate = new Update()
                    .set("status", "DELETED")
                    .set("isArchived", true)
                    .set("removedAt", now)
                    .set("updatedAt", now);
            membersAffected = mongoTemplate.updateMulti(memberQuery, memberUpdate, ConversationMembers.class, COLLECTION_CONVERSATION_MEMBERS).getModifiedCount();

            // 3. Update messages collection: status = STATUS_DELETED (5), isDeleted = true
            Update msgUpdate = new Update()
                    .set("status", Message.STATUS_DELETED)
                    .set("isDeleted", true)
                    .set("editedAt", now);
            messagesAffected = mongoTemplate.updateMulti(msgQuery, msgUpdate, Message.class, COLLECTION_MESSAGES).getModifiedCount();

            // 4. Update reactions collection: mark isDeleted = true
            Update reactionUpdate = new Update()
                    .set("isDeleted", true);
            reactionsAffected = mongoTemplate.updateMulti(reactionQuery, reactionUpdate, Reaction.class, COLLECTION_REACTIONS).getModifiedCount();

            log.info("Soft-deleted chat {}: {} conversations, {} members, {} messages, {} reactions",
                    chatId, convAffected, membersAffected, messagesAffected, reactionsAffected);

            return DeleteChatResponse.builder()
                    .chatId(chatId)
                    .message("Chat soft-deleted successfully across MongoDB collections")
                    .permanent(false)
                    .conversationsAffected(convAffected)
                    .membersAffected(membersAffected)
                    .messagesAffected(messagesAffected)
                    .reactionsAffected(reactionsAffected)
                    .build();
        }
    }

    /**
     * Clear messages and reactions for a chat without deleting the conversation or members.
     * Updates MongoDB messages, reactions, conversations, and conversation_members collections.
     */
    public ClearChatResponse clearChatService(String chatId, boolean permanent) throws Exception {
        validateChatId(chatId);

        Query msgFindQuery = buildChatMessagesQuery(chatId);
        msgFindQuery.fields().include("_id").include("message_id");
        List<Message> messages = mongoTemplate.find(msgFindQuery, Message.class, COLLECTION_MESSAGES);
        List<String> messageIdentifiers = extractMessageIdentifiers(messages);

        Query msgQuery = buildChatMessagesQuery(chatId);
        Query reactionQuery = buildChatReactionsQuery(chatId, messageIdentifiers);

        long messagesAffected;
        long reactionsAffected;

        if (permanent) {
            reactionsAffected = mongoTemplate.remove(reactionQuery, Reaction.class, COLLECTION_REACTIONS).getDeletedCount();
            messagesAffected = mongoTemplate.remove(msgQuery, Message.class, COLLECTION_MESSAGES).getDeletedCount();
        } else {
            Instant now = Instant.now();
            Update msgUpdate = new Update()
                    .set("status", Message.STATUS_DELETED)
                    .set("isDeleted", true)
                    .set("editedAt", now);
            messagesAffected = mongoTemplate.updateMulti(msgQuery, msgUpdate, Message.class, COLLECTION_MESSAGES).getModifiedCount();

            Update reactionUpdate = new Update().set("isDeleted", true);
            reactionsAffected = mongoTemplate.updateMulti(reactionQuery, reactionUpdate, Reaction.class, COLLECTION_REACTIONS).getModifiedCount();
        }

        // Reset last message info in conversations collection
        Query convQuery = buildConversationIdQuery(chatId);
        Update convUpdate = new Update()
                .set("lastMessageId", "")
                .set("lastMessageAt", Instant.now());
        mongoTemplate.updateFirst(convQuery, convUpdate, Conversation.class, COLLECTION_CONVERSATIONS);

        // Reset unread counts in conversation_members collection
        Query memberQuery = buildChatMembersQuery(chatId);
        Update memberUpdate = new Update()
                .set("unreadCount", 0)
                .set("lastReadMessageId", null)
                .set("updatedAt", Instant.now());
        long membersUpdated = mongoTemplate.updateMulti(memberQuery, memberUpdate, ConversationMembers.class, COLLECTION_CONVERSATION_MEMBERS).getModifiedCount();

        log.info("Cleared chat {}: {} messages, {} reactions, {} members reset",
                chatId, messagesAffected, reactionsAffected, membersUpdated);

        return ClearChatResponse.builder()
                .chatId(chatId)
                .message("Chat messages and reactions cleared successfully")
                .permanent(permanent)
                .messagesAffected(messagesAffected)
                .reactionsAffected(reactionsAffected)
                .membersUpdated(membersUpdated)
                .build();
    }

    /**
     * Get members of a chat from MongoDB conversation_members collection.
     */
    public List<ConversationMembers> getChatMembersService(String chatId) throws Exception {
        validateChatId(chatId);
        return findMembersByChatId(chatId);
    }

    /**
     * Update individual member status/settings in MongoDB conversation_members collection.
     */
    public ConversationMembers updateMemberStatusService(String chatId, UpdateMemberStatusRequest request) throws Exception {
        validateChatId(chatId);
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("userId is required to update member status");
        }

        Query query = new Query(new Criteria().andOperator(
                buildMemberConversationCriteria(chatId),
                Criteria.where("userId").is(request.getUserId().trim())
        ));

        ConversationMembers member = mongoTemplate.findOne(query, ConversationMembers.class, COLLECTION_CONVERSATION_MEMBERS);
        if (member == null) {
            throw new IllegalArgumentException("Member not found in chat " + chatId + " with userId: " + request.getUserId());
        }

        Update update = new Update();
        boolean modified = false;

        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            update.set("role", request.getRole().trim());
            member.setRole(request.getRole().trim());
            modified = true;
        }
        if (request.getNickname() != null) {
            update.set("nickname", request.getNickname().trim());
            member.setNickname(request.getNickname().trim());
            modified = true;
        }
        if (request.getIsMuted() != null) {
            update.set("isMuted", request.getIsMuted());
            member.setMuted(request.getIsMuted());
            modified = true;
        }
        if (request.getMuteUntil() != null) {
            update.set("muteUntil", request.getMuteUntil());
            member.setMuteUntil(request.getMuteUntil());
            modified = true;
        }
        if (request.getIsPinned() != null) {
            update.set("isPinned", request.getIsPinned());
            member.setPinned(request.getIsPinned());
            modified = true;
        }
        if (request.getIsArchived() != null) {
            update.set("isArchived", request.getIsArchived());
            member.setArchived(request.getIsArchived());
            modified = true;
        }
        if (request.getNotification() != null && !request.getNotification().trim().isEmpty()) {
            update.set("notification", request.getNotification().trim());
            member.setNotification(request.getNotification().trim());
            modified = true;
        }
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            update.set("status", request.getStatus().trim());
            member.setStatus(request.getStatus().trim());
            modified = true;
        }

        if (modified) {
            Instant now = Instant.now();
            update.set("updatedAt", now);
            member.setUpdatedAt(now);
            mongoTemplate.updateFirst(query, update, ConversationMembers.class, COLLECTION_CONVERSATION_MEMBERS);
            log.info("Updated member {} in chat {}", request.getUserId(), chatId);
        }

        return member;
    }

    /**
     * Get paginated messages for a chat from MongoDB messages collection.
     */
    public PagedResponse<Message> getChatMessagesService(String chatId, int pageNumber, int pageSize) throws Exception {
        validateChatId(chatId);

        int page = Math.max(1, pageNumber);
        int size = Math.max(1, Math.min(100, pageSize));
        int skip = (page - 1) * size;

        Query query = buildChatMessagesQuery(chatId);
        long total = mongoTemplate.count(query, Message.class, COLLECTION_MESSAGES);

        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip(skip)
                .limit(size);

        List<Message> messages = mongoTemplate.find(query, Message.class, COLLECTION_MESSAGES);

        return PagedResponse.of(messages, total, page, size);
    }

    // =========================================================================
    // Chat Helper Methods
    // =========================================================================

    private void validateChatId(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId must not be null or empty");
        }
    }

    private Conversation findConversationById(String chatId) {
        // First try standard repository
        Optional<Conversation> convOpt = conversationRepository.findById(chatId);
        if (convOpt.isPresent()) {
            return convOpt.get();
        }

        // Try MongoTemplate with both string and ObjectId if valid hex
        Query query = buildConversationIdQuery(chatId);
        return mongoTemplate.findOne(query, Conversation.class, COLLECTION_CONVERSATIONS);
    }

    private List<ConversationMembers> findMembersByChatId(String chatId) {
        Query query = buildChatMembersQuery(chatId);
        return mongoTemplate.find(query, ConversationMembers.class, COLLECTION_CONVERSATION_MEMBERS);
    }

    private Query buildConversationIdQuery(String chatId) {
        if (isValidObjectIdHex(chatId)) {
            return new Query(new Criteria().orOperator(
                    Criteria.where("_id").is(chatId),
                    Criteria.where("_id").is(new ObjectId(chatId))
            ));
        }
        return new Query(Criteria.where("_id").is(chatId));
    }

    private Query buildChatMembersQuery(String chatId) {
        return new Query(buildMemberConversationCriteria(chatId));
    }

    private Criteria buildMemberConversationCriteria(String chatId) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("conversationId").is(chatId));
        criteriaList.add(Criteria.where("conversation_id").is(chatId));
        criteriaList.add(Criteria.where("chat_id").is(chatId));
        if (isValidObjectIdHex(chatId)) {
            criteriaList.add(Criteria.where("conversationId").is(new ObjectId(chatId)));
        }
        return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
    }

    private Query buildChatMessagesQuery(String chatId) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("conversation_id").is(chatId));
        criteriaList.add(Criteria.where("conversationId").is(chatId));
        criteriaList.add(Criteria.where("chat_id").is(chatId));
        if (isValidObjectIdHex(chatId)) {
            criteriaList.add(Criteria.where("conversation_id").is(new ObjectId(chatId)));
        }
        return new Query(new Criteria().orOperator(criteriaList.toArray(new Criteria[0])));
    }

    private Query buildChatReactionsQuery(String chatId, List<String> messageIdentifiers) {
        List<Criteria> reactionCriteria = new ArrayList<>();
        reactionCriteria.add(Criteria.where("chat_id").is(chatId));
        reactionCriteria.add(Criteria.where("conversation_id").is(chatId));
        reactionCriteria.add(Criteria.where("conversationId").is(chatId));
        if (isValidObjectIdHex(chatId)) {
            reactionCriteria.add(Criteria.where("chat_id").is(new ObjectId(chatId)));
            reactionCriteria.add(Criteria.where("conversation_id").is(new ObjectId(chatId)));
        }

        if (messageIdentifiers != null && !messageIdentifiers.isEmpty()) {
            reactionCriteria.add(Criteria.where("message_id").in(messageIdentifiers));
            reactionCriteria.add(Criteria.where("messageId").in(messageIdentifiers));
        }

        return new Query(new Criteria().orOperator(reactionCriteria.toArray(new Criteria[0])));
    }

    private List<String> extractMessageIdentifiers(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> ids = new HashSet<>();
        for (Message m : messages) {
            if (m.getId() != null && !m.getId().trim().isEmpty()) {
                ids.add(m.getId().trim());
            }
            if (m.getMessageId() != null && !m.getMessageId().trim().isEmpty()) {
                ids.add(m.getMessageId().trim());
            }
        }
        return new ArrayList<>(ids);
    }
}
