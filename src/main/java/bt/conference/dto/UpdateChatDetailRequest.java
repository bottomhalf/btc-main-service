package bt.conference.dto;

import bt.conference.entity.Conversation.ConversationSettings;
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
public class UpdateChatDetailRequest {

    @JsonProperty("chatId")
    private String chatId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("avatar")
    private String avatar;

    @JsonProperty("type")
    private String type;

    @JsonProperty("settings")
    private ConversationSettings settings;

    @JsonProperty("memberUpdates")
    private List<MemberUpdateDetail> memberUpdates;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MemberUpdateDetail {

        @JsonProperty("userId")
        private String userId;

        @JsonProperty("role")
        private String role;

        @JsonProperty("nickname")
        private String nickname;

        @JsonProperty("isMuted")
        private Boolean isMuted;

        @JsonProperty("muteUntilMillis")
        private Long muteUntilMillis;

        @JsonProperty("isPinned")
        private Boolean isPinned;

        @JsonProperty("isArchived")
        private Boolean isArchived;

        @JsonProperty("notification")
        private String notification;

        @JsonProperty("status")
        private String status;
    }
}
