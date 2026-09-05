package bt.conference.dto;

import bt.conference.entity.Conversation;
import bt.conference.entity.ConversationMembers;
import bt.conference.entity.Message;
import bt.conference.entity.Reaction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatDetailResponse {

    @JsonProperty("chatId")
    private String chatId;

    @JsonProperty("conversation")
    private Conversation conversation;

    @JsonProperty("members")
    private List<ConversationMembers> members;

    @JsonProperty("memberCount")
    private int memberCount;

    @JsonProperty("totalMessages")
    private long totalMessages;

    @JsonProperty("totalReactions")
    private long totalReactions;

    @JsonProperty("recentMessages")
    private List<Message> recentMessages;

    @JsonProperty("reactions")
    private List<Reaction> reactions;
}
