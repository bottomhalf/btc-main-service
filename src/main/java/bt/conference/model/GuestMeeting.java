package bt.conference.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fierhub.database.annotations.Column;
import com.fierhub.database.annotations.Id;
import com.fierhub.database.annotations.Table;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Builder
@Data
@Table(name = "meeting_guest_access")
public class GuestMeeting {

    @Id
    @JsonProperty("id")
    private Long id;

    // ───────── Token Details ─────────
    @Column(name = "access_token")
    @JsonProperty("access_token")
    private String accessToken;

    @Column(name = "meeting_id")
    @JsonProperty("meeting_id")
    private String meetingId;

    // ───────── Usage Rules ─────────
    @Column(name = "max_usage")
    @JsonProperty("max_usage")
    private Integer maxUsage = 1;

    @Column(name = "usage_count")
    @JsonProperty("usage_count")
    private Integer usageCount = 0;

    @Column(name = "is_single_use")
    @JsonProperty("is_single_use")
    private Boolean singleUse = true;

    // ───────── Validity Window ─────────
    @Column(name = "valid_from")
    @JsonProperty("valid_from")
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    private Date validFrom;

    @Column(name = "valid_until")
    @JsonProperty("valid_until")
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    private Date validUntil;

    // ───────── Status ─────────
    @Column(name = "status")
    @JsonProperty("status")
    private TokenStatus status = TokenStatus.ACTIVE;

    // ───────── Guest Info ─────────
    @Column(name = "guest_name")
    @JsonProperty("guest_name")
    private String guestName;

    @Column(name = "guest_email")
    @JsonProperty("guest_email")
    private String guestEmail;

    // ───────── Audit ─────────
    @Column(name = "created_by")
    @JsonProperty("created_by")
    private Long createdBy;

    @Column(name = "created_at")
    @JsonProperty("created_at")
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    private Date createdAt;

    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    private Date updatedAt;

    @Column(name = "used_at")
    @JsonProperty("used_at")
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    private Date usedAt;

    @Column(name = "used_ip")
    @JsonProperty("used_ip")
    private String usedIp;

    @Column(name = "user_agent")
    @JsonProperty("user_agent")
    private String userAgent;
}
