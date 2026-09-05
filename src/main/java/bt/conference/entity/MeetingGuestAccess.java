package bt.conference.entity;

import bt.conference.model.TokenStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fierhub.database.annotations.Column;
import com.fierhub.database.annotations.Id;
import com.fierhub.database.annotations.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Table(name = "meeting_guest_access")
public class MeetingGuestAccess {

    @Id
    @Column(name = "id")
    @JsonProperty("id")
    private Long id;

    @Column(name = "access_token")
    @JsonProperty("access_token")
    private String accessToken;

    @Column(name = "meeting_id")
    @JsonProperty("meeting_id")
    private String meetingId;

    @Column(name = "max_usage")
    @JsonProperty("max_usage")
    @Builder.Default
    private Integer maxUsage = 1;

    @Column(name = "usage_count")
    @JsonProperty("usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "is_single_use")
    @JsonProperty("is_single_use")
    @Builder.Default
    private Boolean isSingleUse = true;

    @Column(name = "valid_from")
    @JsonProperty("valid_from")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date validFrom;

    @Column(name = "valid_until")
    @JsonProperty("valid_until")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date validUntil;

    @Column(name = "status")
    @JsonProperty("status")
    @Builder.Default
    private TokenStatus status = TokenStatus.ACTIVE;

    @Column(name = "guest_name")
    @JsonProperty("guest_name")
    private String guestName;

    @Column(name = "guest_email")
    @JsonProperty("guest_email")
    private String guestEmail;

    @Column(name = "created_by")
    @JsonProperty("created_by")
    private Long createdBy;

    @Column(name = "created_at")
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date createdAt;

    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date updatedAt;

    @Column(name = "used_at")
    @JsonProperty("used_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date usedAt;

    @Column(name = "used_ip")
    @JsonProperty("used_ip")
    private String usedIp;

    @Column(name = "user_agent")
    @JsonProperty("user_agent")
    private String userAgent;
}
