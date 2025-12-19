package bt.conference.entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * Message entity matching the Go model schema.
 * Collection: messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndexes({
        // Primary query pattern: messages in a conversation ordered by time
        @CompoundIndex(name = "conv_created_idx", def = "{'conversation_id': 1, 'created_at': -1}"),

        // Find messages by sender in a conversation
        @CompoundIndex(name = "conv_sender_idx", def = "{'conversation_id': 1, 'sender_id': 1, 'created_at': -1}"),

        // Find messages by status (for unread, delivered, etc.)
        @CompoundIndex(name = "conv_status_idx", def = "{'conversation_id': 1, 'status': 1, 'created_at': -1}"),

        // Find messages by receiver (for delivery status updates)
        @CompoundIndex(name = "receiver_status_idx", def = "{'recieved_id': 1, 'status': 1, 'created_at': -1}"),

        // Find messages by type (text, image, file, etc.)
        @CompoundIndex(name = "conv_type_idx", def = "{'conversation_id': 1, 'type': 1, 'created_at': -1}"),

        // Find reply threads
        @CompoundIndex(name = "reply_thread_idx", def = "{'reply_to': 1, 'created_at': 1}"),

        // Find messages mentioning users
        @CompoundIndex(name = "mentions_idx", def = "{'mentions': 1, 'created_at': -1}"),

        // Unique message_id lookup
        @CompoundIndex(name = "message_id_unique_idx", def = "{'message_id': 1}", unique = true)
})
public class Message {

    @Id
    private String id;

    /**
     * Unique message identifier (UUID or client-generated ID)
     */
    @Field("message_id")
    @Indexed(unique = true)
    private String messageId;

    /**
     * Reference to the conversation this message belongs to
     */
    @Field("conversation_id")
    @Indexed
    private String conversationId;

    /**
     * ID of the user who sent this message
     */
    @Field("sender_id")
    @Indexed
    private String senderId;

    /**
     * ID of the intended receiver (for direct messages)
     */
    @Field("recieved_id")
    private String recievedId;

    /**
     * Message type: text, image, video, file, audio, location, etc.
     */
    @Field("type")
    private String type;

    /**
     * Sender's avatar URL at time of message
     */
    @Field("avatar")
    private String avatar;

    /**
     * Message content/body - main searchable field
     */
    @TextIndexed(weight = 10)
    @Field("body")
    private String body;

    /**
     * URL for attached file (image, video, document, etc.)
     */
    @Field("file_url")
    private String fileUrl;

    /**
     * Reference to parent message if this is a reply
     */
    @Field("reply_to")
    private String replyTo;

    /**
     * List of user IDs mentioned in this message
     */
    @Field("mentions")
    private List<Long> mentions;

    /**
     * Reactions on this message
     */
    @Field("reactions")
    private List<Reaction> reactions;

    /**
     * Client type: web, mobile, desktop, api
     */
    @Field("client_type")
    private String clientType;

    /**
     * When the message was created
     */
    @Field("created_at")
    @Indexed
    private Instant createdAt;

    /**
     * When the message was last edited (null if never edited)
     */
    @Field("edited_at")
    private Instant editedAt;

    /**
     * Message status:
     * 1 = Received
     * 2 = Sent
     * 3 = Seen
     * 4 = Edited
     * 5 = Deleted
     */
    @Field("status")
    @Indexed
    private Integer status;

    /**
     * Flag indicating if this message started a new conversation
     */
    @Field("is_new_conversation")
    private Boolean isNewConversation;

    // ==================== Status Constants ====================

    public static final int STATUS_RECEIVED = 1;
    public static final int STATUS_SENT = 2;
    public static final int STATUS_SEEN = 3;
    public static final int STATUS_EDITED = 4;
    public static final int STATUS_DELETED = 5;

    // ==================== Message Type Constants ====================

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_LOCATION = "location";
    public static final String TYPE_STICKER = "sticker";
    public static final String TYPE_SYSTEM = "system";

    // ==================== Helper Methods ====================

    public boolean isDeleted() {
        return status != null && status == STATUS_DELETED;
    }

    public boolean isEdited() {
        return editedAt != null || (status != null && status == STATUS_EDITED);
    }

    public boolean isSeen() {
        return status != null && status == STATUS_SEEN;
    }

    public boolean hasAttachment() {
        return fileUrl != null && !fileUrl.isEmpty();
    }

    public boolean isReply() {
        return replyTo != null;
    }

    public boolean hasMentions() {
        return mentions != null && !mentions.isEmpty();
    }

    public boolean hasReactions() {
        return reactions != null && !reactions.isEmpty();
    }

    /**
     * Get the string representation of the String for JSON serialization
     */
    public String getIdAsString() {
        return id != null ? id : null;
    }

    public String getConversationIdAsString() {
        return conversationId != null ? conversationId : null;
    }

    public String getReplyToAsString() {
        return replyTo != null ? replyTo : null;
    }

    // ==================== Nested Classes ====================

    /**
     * Reaction on a message
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reaction {

        @Field("user_id")
        private Long userId;

        @Field("emoji")
        private String emoji;
    }

    /**
     * Message status tracking (can be used for detailed delivery info)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageStatus {

        @Field("received")
        private Long received;

        @Field("sent")
        private Long sent;

        @Field("seen")
        private Long seen;

        @Field("edited")
        private Long edited;

        @Field("deleted")
        private Long deleted;
    }
}