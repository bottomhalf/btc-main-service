package bt.conference.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Reaction entity representing reactions on messages within a chat/conversation.
 * Collection: reactions
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "reactions")
@CompoundIndexes({
        @CompoundIndex(name = "conv_reaction_idx", def = "{'conversation_id': 1, 'created_at': -1}"),
        @CompoundIndex(name = "chat_reaction_idx", def = "{'chat_id': 1, 'created_at': -1}"),
        @CompoundIndex(name = "msg_reaction_idx", def = "{'message_id': 1, 'created_at': -1}")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Reaction {

    @Id
    private String id;

    @Field("chat_id")
    @JsonProperty("chat_id")
    @Indexed
    private String chatId;

    @Field("conversation_id")
    @JsonProperty("conversation_id")
    @Indexed
    private String conversationId;

    @Field("message_id")
    @JsonProperty("message_id")
    @Indexed
    private String messageId;

    @Field("user_id")
    @JsonProperty("user_id")
    @Indexed
    private String userId;

    @Field("emoji")
    private String emoji;

    @Field("created_at")
    @JsonProperty("created_at")
    private Instant createdAt;

    @Field("is_deleted")
    @JsonProperty("is_deleted")
    private Boolean isDeleted;
}
