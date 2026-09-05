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
public class ClearChatResponse {

    @JsonProperty("chatId")
    private String chatId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("permanent")
    private boolean permanent;

    @JsonProperty("messagesAffected")
    private long messagesAffected;

    @JsonProperty("reactionsAffected")
    private long reactionsAffected;

    @JsonProperty("membersUpdated")
    private long membersUpdated;
}
