// Conversation.java
package bt.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation {

    @Id
    private String id;

    @Field("conversation_type")
    private String conversationType;

    @Field("participant_ids")
    private List<String> participantIds;

    @Field("participants")
    private List<Participant> participants;

    @Field("conversation_name")
    private String conversationName;

    @Field("conversation_avatar")
    private String conversationAvatar;

    @Field("created_by")
    private String createdBy;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Field("last_message")
    private LastMessage lastMessage;

    @Field("last_message_at")
    private Instant lastMessageAt;

    @Field("is_active")
    private boolean isActive;

    @Field("settings")
    private ConversationSettings settings;

    @Data
    @Builder
    @AllArgsConstructor
    public static class Participant {
        @Field("user_id")
        private String userId;

        @Field("username")
        private String username;

        @Field("email")
        private String email;

        @Field("avatar")
        private String avatar;

        @Field("joined_at")
        private Instant joinedAt;

        @Field("role")
        private String role;
    }

    @Data
    public static class LastMessage {
        @Field("message_id")
        private String messageId;

        @Field("content")
        private String content;

        @Field("sender_id")
        private String senderId;

        @Field("sender_name")
        private String senderName;

        @Field("sent_at")
        private Instant sentAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ConversationSettings {
        @Field("allow_reactions")
        private boolean allowReactions;

        @Field("allow_pinning")
        private boolean allowPinning;

        @Field("admin_only_post")
        private boolean adminOnlyPost;
    }
}