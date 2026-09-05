package bt.conference.repository;

import bt.conference.entity.MeetingDetail;
import com.fierhub.database.service.DbManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MeetingDetailRepository {

    private final DbManager dbManager;

    /**
     * Find meeting detail by numeric primary key
     */
    public Optional<MeetingDetail> findById(long meetingDetailId) {
        try {
            MeetingDetail detail = dbManager.getById(meetingDetailId, MeetingDetail.class);
            if (detail == null) {
                detail = dbManager.queryRaw("select * from meeting_detail where meetingDetailId = " + meetingDetailId, MeetingDetail.class);
            }
            return Optional.ofNullable(detail);
        } catch (Exception e) {
            log.error("Error finding meeting detail by meetingDetailId: {}", meetingDetailId, e);
            return Optional.empty();
        }
    }

    /**
     * Find meeting detail by string meetingId token
     */
    public Optional<MeetingDetail> findByMeetingId(String meetingId) {
        try {
            MeetingDetail detail = dbManager.queryRaw("select * from meeting_detail where meetingId = '" + meetingId + "'", MeetingDetail.class);
            return Optional.ofNullable(detail);
        } catch (Exception e) {
            log.error("Error finding meeting detail by meetingId: {}", meetingId, e);
            return Optional.empty();
        }
    }

    /**
     * Find all meetings organized by a specific user
     */
    public List<MeetingDetail> findByOrganizedBy(long organizedBy) {
        try {
            return dbManager.queryList("select * from meeting_detail where organizedBy = " + organizedBy, MeetingDetail.class);
        } catch (Exception e) {
            log.error("Error finding meetings for organizedBy: {}", organizedBy, e);
            return List.of();
        }
    }

    /**
     * Save or update meeting detail in MySQL (uses ON DUPLICATE KEY UPDATE)
     */
    public MeetingDetail save(MeetingDetail meetingDetail) throws Exception {
        dbManager.save(meetingDetail);
        return meetingDetail;
    }
}
