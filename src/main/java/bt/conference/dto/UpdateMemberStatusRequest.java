package bt.conference.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateMemberStatusRequest {

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("role")
    private String role;

    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("isMuted")
    private Boolean isMuted;

    @JsonProperty("muteUntil")
    private Instant muteUntil;

    @JsonProperty("isPinned")
    private Boolean isPinned;

    @JsonProperty("isArchived")
    private Boolean isArchived;

    @JsonProperty("notification")
    private String notification;

    @JsonProperty("status")
    private String status;
}
