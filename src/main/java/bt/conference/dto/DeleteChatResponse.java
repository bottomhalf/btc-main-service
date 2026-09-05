package bt.conference.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteChatResponse {

    @JsonProperty("chatId")
    private String chatId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("permanent")
    private boolean permanent;

    @JsonProperty("conversationsAffected")
    private long conversationsAffected;

    @JsonProperty("membersAffected")
    private long membersAffected;

    @JsonProperty("messagesAffected")
    private long messagesAffected;

    @JsonProperty("reactionsAffected")
    private long reactionsAffected;
}
