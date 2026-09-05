package bt.conference.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fierhub.database.annotations.Column;
import com.fierhub.database.annotations.Id;
import com.fierhub.database.annotations.Table;
import com.fierhub.database.annotations.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Table(name = "meeting_detail")
public class MeetingDetail {
    @Id
    @Column(name = "meetingDetailId")
    long meetingDetailId;

    @Column(name = "meetingId")
    String meetingId;

    @Column(name = "meetingPassword")
    String meetingPassword;

    @Column(name = "organizedBy")
    long organizedBy;

    @Column(name = "agenda")
    String agenda;

    @Column(name = "title")
    String title;

    @Column(name = "startDate")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    Date startDate;

    @Column(name = "startTime")
    String startTime;

    @Column(name = "endDate")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    Date endDate;

    @Column(name = "endTime")
    String endTime;

    @Column(name = "durationInSecond")
    int durationInSecond;

    @Transient
    String organizerName;

    @Column(name = "hasQuickMeeting")
    boolean hasQuickMeeting;

    @Column(name = "conversationId")
    String conversationId;

    @Column(name = "isAllDay")
    boolean isAllDay;

    @Column(name = "repeatType")
    int repeatType;

    @Column(name = "participants")
    String participants;

    @Transient
    List<String> participantsId;

    @Transient
    int participantCount;

    @Transient
    List<Conversation.Participant> participantsDetail;
}
