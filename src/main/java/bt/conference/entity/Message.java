package bt.conference.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "messages")
public class Message {

    @Id
    private String id; // Maps to "_id"

    private String messageId; // Maps to "message_id"
    private String senderId; // Maps to "sender_id"
    private String conversationId; // Maps to "conversation_id"
    private String type; // Maps to "type"
    private String avatar; // Maps to "type"
    private String body; // Maps to "body"
    private String fileUrl; // Maps to "file_url"
    private String replyTo; // Maps to "reply_to"
    private List<Long> mentions; // Maps to "mentions"
    private List<Reaction> reactions; // Maps to "reactions"
    private String clientType; // Maps to "web, mobile, desktop"
    private Instant createdAt; // Maps to "created_at"
    private Instant editedAt; // Maps to "edited_at"
    private int status; // Maps to "status"

    @Data
    public static class Reaction {
        private long userId; // Maps to "user_id"
        private String emoji; // Maps to "emoji"
    }
}