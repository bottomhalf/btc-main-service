package bt.conference.repository;

import bt.conference.config.SearchConfig;
import bt.conference.entity.Message;
import bt.conference.model.MessageSearchResult;
import bt.conference.service.GlobalSearchException;
import bt.conference.service.GlobalSearchException.ErrorType;

import bt.conference.service.SearchCacheService;
import bt.conference.service.SearchExecutorService;
import com.fierhub.model.UserSession;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Repository
public class MessageSearchRepository extends BaseSearchRepository<Message> {

    private static final String COLLECTION_NAME = "messages";
    private static final String CACHE_NAMESPACE = "msg";

    private final UserSession userSession;
    private final MongoTemplate mongoTemplate;
    private final SearchExecutorService executorService;
    private final SearchCacheService cacheService;
    private final SearchConfig config;

    @Autowired
    public MessageSearchRepository(
            MongoTemplate mongoTemplate,
            SearchExecutorService executorService,
            SearchCacheService cacheService,
            SearchConfig config,
            UserSession userSession) {
        super(mongoTemplate, executorService, cacheService, config);
        this.userSession = userSession;
        this.cacheService = cacheService;
        this.config = config;
        this.mongoTemplate = mongoTemplate;
        this.executorService = executorService;
    }

    @PostConstruct
    public void init() {
        logIndexRecommendations();
    }

    // ==================== Abstract Method Implementations ====================

    @Override
    protected String getCollectionName() {
        return COLLECTION_NAME;
    }

    @Override
    protected Class<Message> getEntityClass() {
        return Message.class;
    }

    @Override
    protected String getCacheNamespace() {
        return CACHE_NAMESPACE;
    }

    @Override
    protected String[] getTextSearchFields() {
        return new String[]{"body"};
    }

    @Override
    protected Criteria buildRegexSearchCriteria(Pattern pattern) {
        return Criteria.where("body").regex(pattern);
    }

    @Override
    protected String getDefaultSortField() {
        return "created_at";
    }

    // ==================== Message Search with Criteria ====================

    /**
     * Search messages with comprehensive filters.
     */
    public MessageSearchResult searchMessages(MessageSearchCriteria criteria) {
        validateSearchCriteria(criteria);
        executorService.checkRateLimit(userSession.getUserId());
        totalSearches.incrementAndGet();

        String cacheKey = buildMessageCacheKey(criteria);
        MessageSearchResult cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            Query query = buildMessageSearchQuery(criteria);

            // Get total count
            long totalCount = mongoTemplate.count(query, Message.class, COLLECTION_NAME);

            // Apply pagination
            query.skip(criteria.getSkip()).limit(criteria.getLimit());
            query.with(Sort.by(
                    criteria.getSortDirection(),
                    criteria.getSortField() != null ? criteria.getSortField() : "created_at"
            ));

            List<Message> messages = mongoTemplate.find(query, Message.class, COLLECTION_NAME);

            MessageSearchResult result = MessageSearchResult.builder()
                    .messages(messages)
                    .totalCount(totalCount)
                    .page(criteria.getSkip() / criteria.getLimit())
                    .pageSize(criteria.getLimit())
                    .hasMore(criteria.getSkip() + messages.size() < totalCount)
                    .build();

            cacheService.put(cacheKey, result);
            return result;

        } catch (MongoTimeoutException e) {
            throw new GlobalSearchException(ErrorType.TIMEOUT, criteria.getSearchTerm(), e);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, criteria.getSearchTerm(), e);
        }
    }

    /**
     * Typeahead search for messages - fast, limited results.
     */
    public List<Message> typeAheadMessages(String searchTerm, String conversationId, int limit) {
        validateSearchTerm(searchTerm);
        executorService.checkRateLimit(userSession.getUserId());

        String cacheKey = cacheService.buildKey(CACHE_NAMESPACE,
                searchTerm + ":" + (conversationId != null ? conversationId : "all"),
                userSession.getUserId(), true, 0, limit);

        List<Message> cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            Query query;
            if (config.isUseTextIndex()) {
                TextCriteria textCriteria = TextCriteria.forDefaultLanguage()
                        .matchingPhrase(searchTerm);
                query = TextQuery.queryText(textCriteria).sortByScore();
            } else {
                Pattern pattern = buildContainsPattern(searchTerm);
                query = new Query(Criteria.where("body").regex(pattern));
            }

            // Exclude deleted messages
            query.addCriteria(Criteria.where("status").ne(Message.STATUS_DELETED));

            if (conversationId != null) {
                query.addCriteria(Criteria.where("conversation_id").is(new ObjectId(conversationId)));
            }

            query.limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            List<Message> results = mongoTemplate.find(query, Message.class, COLLECTION_NAME);
            cacheService.put(cacheKey, results);
            return results;

        } catch (MongoException e) {
            logger.warn("Typeahead search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== Find by ID Methods ====================

    /**
     * Find message by MongoDB ObjectId.
     */
    public Optional<Message> findById(String id) {
        try {
            Message result = mongoTemplate.findById(new ObjectId(id), Message.class, COLLECTION_NAME);
            return Optional.ofNullable(result);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid ObjectId: {}", id);
            return Optional.empty();
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, id, e);
        }
    }

    /**
     * Find message by unique message_id field.
     */
    public Optional<Message> findByMessageId(String messageId) {
        try {
            Query query = new Query(Criteria.where("message_id").is(messageId));
            Message result = mongoTemplate.findOne(query, Message.class, COLLECTION_NAME);
            return Optional.ofNullable(result);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, messageId, e);
        }
    }

    // ==================== Find by Conversation ====================

    /**
     * Find messages by conversation ID with pagination.
     */
    public List<Message> findByConversationId(String conversationId, int skip, int limit) {
        return findByConversationId(conversationId, skip, limit, null, null, false);
    }

    /**
     * Find messages by conversation with date range and optional include deleted.
     */
    public List<Message> findByConversationId(String conversationId, int skip, int limit,
                                              Instant after, Instant before, boolean includeDeleted) {
        try {
            Criteria criteria = Criteria.where("conversation_id").is(new ObjectId(conversationId));

            if (!includeDeleted) {
                criteria = criteria.and("status").ne(Message.STATUS_DELETED);
            }

            if (after != null) {
                criteria = criteria.and("created_at").gte(after);
            }
            if (before != null) {
                criteria = criteria.and("created_at").lte(before);
            }

            Query query = new Query(criteria);
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, conversationId, e);
        }
    }

    // ==================== Find by Sender/Receiver ====================

    /**
     * Find messages by sender ID.
     */
    public List<Message> findBySenderId(String senderId, int skip, int limit) {
        try {
            Query query = new Query(Criteria.where("sender_id").is(senderId)
                    .and("status").ne(Message.STATUS_DELETED));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, senderId, e);
        }
    }

    /**
     * Find messages by sender in a specific conversation.
     */
    public List<Message> findBySenderInConversation(String conversationId, String senderId,
                                                    int skip, int limit) {
        try {
            Query query = new Query(Criteria.where("conversation_id").is(new ObjectId(conversationId))
                    .and("sender_id").is(senderId)
                    .and("status").ne(Message.STATUS_DELETED));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, conversationId, e);
        }
    }

    /**
     * Find messages by receiver ID (for direct messages).
     */
    public List<Message> findByReceiverId(String receiverId, int skip, int limit) {
        try {
            Query query = new Query(Criteria.where("recieved_id").is(receiverId)
                    .and("status").ne(Message.STATUS_DELETED));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, receiverId, e);
        }
    }

    // ==================== Find by Status ====================

    /**
     * Find messages by status in a conversation.
     */
    public List<Message> findByStatus(String conversationId, int status, int skip, int limit) {
        try {
            Query query = new Query(Criteria.where("conversation_id").is(new ObjectId(conversationId))
                    .and("status").is(status));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, conversationId, e);
        }
    }

    /**
     * Find unread (received but not seen) messages for a user.
     */
    public List<Message> findUnreadMessages(String receiverId, int limit) {
        try {
            Query query = new Query(Criteria.where("recieved_id").is(receiverId)
                    .and("status").in(Message.STATUS_RECEIVED, Message.STATUS_SENT));
            query.limit(limit);
            query.with(Sort.by(Sort.Direction.ASC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, receiverId, e);
        }
    }

    /**
     * Find unread messages in a specific conversation.
     */
    public List<Message> findUnreadInConversation(String conversationId, String receiverId, int limit) {
        try {
            Query query = new Query(Criteria.where("conversation_id").is(new ObjectId(conversationId))
                    .and("recieved_id").is(receiverId)
                    .and("status").in(Message.STATUS_RECEIVED, Message.STATUS_SENT));
            query.limit(limit);
            query.with(Sort.by(Sort.Direction.ASC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, conversationId, e);
        }
    }

    /**
     * Count unread messages for a user.
     */
    public long countUnreadMessages(String receiverId) {
        try {
            Query query = new Query(Criteria.where("recieved_id").is(receiverId)
                    .and("status").in(Message.STATUS_RECEIVED, Message.STATUS_SENT));
            return mongoTemplate.count(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, receiverId, e);
        }
    }

    /**
     * Count unread messages in a conversation.
     */
    public long countUnreadInConversation(String conversationId, String receiverId) {
        try {
            Query query = new Query(Criteria.where("conversation_id").is(new ObjectId(conversationId))
                    .and("recieved_id").is(receiverId)
                    .and("status").in(Message.STATUS_RECEIVED, Message.STATUS_SENT));
            return mongoTemplate.count(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, conversationId, e);
        }
    }

    // ==================== Find by Type ====================

    /**
     * Find messages by type in a conversation.
     */
    public List<Message> findByType(String conversationId, String type, int skip, int limit) {
        try {
            Query query = new Query(Criteria.where("conversation_id").is(new ObjectId(conversationId))
                    .and("type").is(type)
                    .and("status").ne(Message.STATUS_DELETED));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, conversationId, e);
        }
    }

    /**
     * Find messages with attachments (file_url is not null).
     */
    public List<Message> findWithAttachments(String conversationId, int skip, int limit) {
        try {
            Query query = new Query(Criteria.where("conversation_id").is(new ObjectId(conversationId))
                    .and("file_url").exists(true).ne(null)
                    .and("status").ne(Message.STATUS_DELETED));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, conversationId, e);
        }
    }

    // ==================== Find by Mentions ====================

    /**
     * Find messages mentioning a specific user.
     */
    public List<Message> findByMention(Long userId, int skip, int limit) {
        try {
            Query query = new Query(Criteria.where("mentions").in(userId)
                    .and("status").ne(Message.STATUS_DELETED));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, String.valueOf(userId), e);
        }
    }

    /**
     * Find messages mentioning a user in a specific conversation.
     */
    public List<Message> findByMentionInConversation(String conversationId, Long userId,
                                                     int skip, int limit) {
        try {
            Query query = new Query(Criteria.where("conversation_id").is(new ObjectId(conversationId))
                    .and("mentions").in(userId)
                    .and("status").ne(Message.STATUS_DELETED));
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, conversationId, e);
        }
    }

    // ==================== Find Replies/Threads ====================

    /**
     * Find replies to a specific message.
     */
    public List<Message> findReplies(String parentMessageId, int limit) {
        try {
            Query query = new Query(Criteria.where("reply_to").is(new ObjectId(parentMessageId))
                    .and("status").ne(Message.STATUS_DELETED));
            query.limit(limit);
            query.with(Sort.by(Sort.Direction.ASC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, parentMessageId, e);
        }
    }

    /**
     * Count replies to a message.
     */
    public long countReplies(String parentMessageId) {
        try {
            Query query = new Query(Criteria.where("reply_to").is(new ObjectId(parentMessageId))
                    .and("status").ne(Message.STATUS_DELETED));
            return mongoTemplate.count(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, parentMessageId, e);
        }
    }

    // ==================== Date Range Queries ====================

    /**
     * Find messages in a date range.
     */
    public List<Message> findByDateRange(String conversationId, Instant start, Instant end,
                                         int skip, int limit) {
        try {
            Criteria criteria = Criteria.where("created_at").gte(start).lte(end)
                    .and("status").ne(Message.STATUS_DELETED);

            if (conversationId != null) {
                criteria = criteria.and("conversation_id").is(new ObjectId(conversationId));
            }

            Query query = new Query(criteria);
            query.skip(skip).limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, "dateRange", e);
        }
    }

    // ==================== Recent Messages ====================

    /**
     * Get recent messages across multiple conversations.
     */
    public List<Message> findRecentInConversations(List<String> conversationIds, int limit) {
        try {
            List<ObjectId> objectIds = conversationIds.stream()
                    .map(ObjectId::new)
                    .toList();

            Query query = new Query(Criteria.where("conversation_id").in(objectIds)
                    .and("status").ne(Message.STATUS_DELETED));
            query.limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "created_at"));

            return mongoTemplate.find(query, Message.class, COLLECTION_NAME);
        } catch (MongoException e) {
            throw new GlobalSearchException(ErrorType.DATABASE_ERROR, "recent", e);
        }
    }

    /**
     * Get last message for each conversation (for conversation list preview).
     */
    public Map<String, Message> findLastMessagePerConversation(List<String> conversationIds) {
        Map<String, Message> lastMessages = new HashMap<>();

        for (String convId : conversationIds) {
            try {
                Query query = new Query(Criteria.where("conversation_id").is(new ObjectId(convId))
                        .and("status").ne(Message.STATUS_DELETED));
                query.limit(1);
                query.with(Sort.by(Sort.Direction.DESC, "created_at"));

                Message lastMsg = mongoTemplate.findOne(query, Message.class, COLLECTION_NAME);
                if (lastMsg != null) {
                    lastMessages.put(convId, lastMsg);
                }
            } catch (MongoException e) {
                logger.warn("Failed to get last message for conversation {}: {}", convId, e.getMessage());
            }
        }

        return lastMessages;
    }

    // ==================== Private Helper Methods ====================

    private Query buildMessageSearchQuery(MessageSearchCriteria criteria) {
        List<Criteria> criteriaList = new ArrayList<>();

        // Exclude deleted messages by default
        if (!Boolean.TRUE.equals(criteria.getIncludeDeleted())) {
            criteriaList.add(Criteria.where("status").ne(Message.STATUS_DELETED));
        }

        // Text/body search
        if (criteria.getSearchTerm() != null && !criteria.getSearchTerm().isEmpty()) {
            if (config.isUseTextIndex()) {
                TextCriteria textCriteria = TextCriteria.forDefaultLanguage()
                        .matchingPhrase(criteria.getSearchTerm());
                Query textQuery = TextQuery.queryText(textCriteria).sortByScore();
                for (Criteria c : criteriaList) {
                    textQuery.addCriteria(c);
                }
                addFilterCriteria(textQuery, criteria);
                return textQuery;
            } else {
                Pattern pattern = buildContainsPattern(criteria.getSearchTerm());
                criteriaList.add(Criteria.where("body").regex(pattern));
            }
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        addFilterCriteria(query, criteria);
        return query;
    }

    private void addFilterCriteria(Query query, MessageSearchCriteria criteria) {
        if (criteria.getConversationId() != null) {
            query.addCriteria(Criteria.where("conversation_id")
                    .is(new ObjectId(criteria.getConversationId())));
        }

        if (criteria.getConversationIds() != null && !criteria.getConversationIds().isEmpty()) {
            List<ObjectId> objectIds = criteria.getConversationIds().stream()
                    .map(ObjectId::new)
                    .toList();
            query.addCriteria(Criteria.where("conversation_id").in(objectIds));
        }

        if (criteria.getSenderId() != null) {
            query.addCriteria(Criteria.where("sender_id").is(criteria.getSenderId()));
        }

        if (criteria.getReceiverId() != null) {
            query.addCriteria(Criteria.where("recieved_id").is(criteria.getReceiverId()));
        }

        if (criteria.getType() != null) {
            query.addCriteria(Criteria.where("type").is(criteria.getType()));
        }

        if (criteria.getStatus() != null) {
            query.addCriteria(Criteria.where("status").is(criteria.getStatus()));
        }

        if (Boolean.TRUE.equals(criteria.getHasAttachment())) {
            query.addCriteria(Criteria.where("file_url").exists(true).ne(null));
        }

        if (criteria.getMentionUserId() != null) {
            query.addCriteria(Criteria.where("mentions").in(criteria.getMentionUserId()));
        }

        if (criteria.getReplyTo() != null) {
            query.addCriteria(Criteria.where("reply_to").is(new ObjectId(criteria.getReplyTo())));
        }

        if (criteria.getAfter() != null) {
            query.addCriteria(Criteria.where("created_at").gte(criteria.getAfter()));
        }

        if (criteria.getBefore() != null) {
            query.addCriteria(Criteria.where("created_at").lte(criteria.getBefore()));
        }

        if (criteria.getClientType() != null) {
            query.addCriteria(Criteria.where("client_type").is(criteria.getClientType()));
        }
    }

    private void validateSearchCriteria(MessageSearchCriteria criteria) {
        if (criteria.getSearchTerm() != null) {
            validateSearchTerm(criteria.getSearchTerm());
        }

        // Validate and set defaults
        if (criteria.getLimit() <= 0) {
            criteria.setLimit(config.getDefaultPageSize());
        }
        if (criteria.getLimit() > config.getMaxPageSize()) {
            criteria.setLimit(config.getMaxPageSize());
        }
        if (criteria.getSkip() < 0) {
            criteria.setSkip(0);
        }
    }

    private String buildMessageCacheKey(MessageSearchCriteria criteria) {
        StringBuilder keyBuilder = new StringBuilder(CACHE_NAMESPACE);
        keyBuilder.append(":search:");

        if (criteria.getSearchTerm() != null) {
            keyBuilder.append(criteria.getSearchTerm().toLowerCase()).append(":");
        }
        if (criteria.getConversationId() != null) {
            keyBuilder.append("conv:").append(criteria.getConversationId()).append(":");
        }
        if (criteria.getSenderId() != null) {
            keyBuilder.append("sender:").append(criteria.getSenderId()).append(":");
        }
        if (criteria.getStatus() != null) {
            keyBuilder.append("status:").append(criteria.getStatus()).append(":");
        }
        keyBuilder.append(criteria.getSkip()).append(":").append(criteria.getLimit());

        return keyBuilder.toString();
    }

    @Override
    public void logIndexRecommendations() {
        logger.info("=== MongoDB Index Recommendations for Messages Collection ===");
        logger.info("Run these commands in MongoDB shell:");
        logger.info("");
        logger.info("// Text index for full-text search on body");
        logger.info("db.messages.createIndex({ body: 'text' }, { name: 'message_body_text' });");
        logger.info("");
        logger.info("// Primary indexes for common query patterns");
        logger.info("db.messages.createIndex({ message_id: 1 }, { unique: true, name: 'message_id_unique_idx' });");
        logger.info("db.messages.createIndex({ conversation_id: 1, created_at: -1 }, { name: 'conv_created_idx' });");
        logger.info("db.messages.createIndex({ conversation_id: 1, sender_id: 1, created_at: -1 }, { name: 'conv_sender_idx' });");
        logger.info("db.messages.createIndex({ conversation_id: 1, status: 1, created_at: -1 }, { name: 'conv_status_idx' });");
        logger.info("");
        logger.info("// Indexes for receiver and unread queries");
        logger.info("db.messages.createIndex({ recieved_id: 1, status: 1, created_at: -1 }, { name: 'receiver_status_idx' });");
        logger.info("");
        logger.info("// Indexes for filtering by type and attachments");
        logger.info("db.messages.createIndex({ conversation_id: 1, type: 1, created_at: -1 }, { name: 'conv_type_idx' });");
        logger.info("db.messages.createIndex({ conversation_id: 1, file_url: 1, created_at: -1 }, { name: 'conv_attachment_idx' });");
        logger.info("");
        logger.info("// Indexes for threads and mentions");
        logger.info("db.messages.createIndex({ reply_to: 1, created_at: 1 }, { name: 'reply_thread_idx' });");
        logger.info("db.messages.createIndex({ mentions: 1, created_at: -1 }, { name: 'mentions_idx' });");
        logger.info("================================================================");
    }

    // ==================== Search Criteria DTO ====================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MessageSearchCriteria {
        private String searchTerm;           // Search in body
        private String conversationId;       // Single conversation
        private List<String> conversationIds; // Multiple conversations
        private String senderId;             // Filter by sender
        private String receiverId;           // Filter by receiver
        private String type;                 // Message type (text, image, etc.)
        private Integer status;              // Message status (1-5)
        private Boolean hasAttachment;       // Has file_url
        private Long mentionUserId;          // Mentioned user
        private String replyTo;              // Reply to message ID
        private String clientType;           // Client type filter
        private Instant after;               // Created after
        private Instant before;              // Created before
        private Boolean includeDeleted;      // Include deleted messages

        @lombok.Builder.Default
        private int skip = 0;
        @lombok.Builder.Default
        private int limit = 20;

        private String sortField;
        @lombok.Builder.Default
        private Sort.Direction sortDirection = Sort.Direction.DESC;
    }
}