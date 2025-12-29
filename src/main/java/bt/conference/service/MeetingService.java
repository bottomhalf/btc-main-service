package bt.conference.service;

import bt.conference.dto.PagedResponse;
import bt.conference.entity.Conversation;
import bt.conference.entity.MeetingDetail;
import bt.conference.entity.UserDetail;
import bt.conference.serviceinterface.IMeetingService;
import com.fierhub.database.service.DbManager;
import com.fierhub.database.utils.DbParameters;
import com.fierhub.database.utils.DbProcedureManager;
import com.fierhub.model.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

@Service
public class MeetingService implements IMeetingService {
    @Autowired
    UserSession userSession;
    @Autowired
    DbProcedureManager dbProcedureManager;
    @Autowired
    DbManager dbManager;
    @Autowired
    ConversationService conversationService;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom random = new SecureRandom();
    public PagedResponse<Conversation> generateMeetingService(MeetingDetail meetingDetail) throws Exception {
        if (meetingDetail.getTitle() == null || meetingDetail.getTitle().isEmpty())
            throw new Exception("Invalid meeting title");

        if (meetingDetail.getDurationInSecond() <= 0)
            throw new Exception("Invalid meeting duration");

        var convertedDate = UtilService.toUtc(meetingDetail.getStartDate());
        meetingDetail.setStartDate(convertedDate);
        meetingDetail.setHasQuickMeeting(false);
        addMeetingDetail(meetingDetail);

        return getRecentMeetingsService();
    }

    private void addMeetingDetail(MeetingDetail meetingDetail) throws Exception {
        var user = dbManager.getById(userSession.getUserId(), UserDetail.class);
        if (user == null)
            throw new Exception("User not found");

        var fullName = user.getFirstName() + (user.getLastName() != null && !user.getLastName().isEmpty() ? " " + user.getLastName() : "");
        meetingDetail.setMeetingId(ManageMeetingService.generateToken(Long.parseLong(userSession.getUserId()), fullName));
        meetingDetail.setMeetingPassword(generatePassword(6));
        meetingDetail.setOrganizedBy(Long.parseLong(userSession.getUserId()));

        dbManager.save(meetingDetail);
    }

    public PagedResponse<Conversation> getRecentMeetingsService() throws Exception {
        int userId = Integer.parseInt(userSession.getUserId().replace(userSession.getCode(), ""));
        return conversationService.searchConversationsRecentGroup(String.valueOf(userId), 1, 5);
    }

    public PagedResponse<Conversation> generateQuickMeetingService(MeetingDetail meetingDetail) throws Exception {
        meetingDetail.setDurationInSecond(36000);
        java.util.Date utilDate = new java.util.Date();
        var date = new java.sql.Timestamp(utilDate.getTime());
        meetingDetail.setStartDate(date);
        meetingDetail.setHasQuickMeeting(true);

        addMeetingDetail(meetingDetail);
        return getRecentMeetingsService();
    }

    public  MeetingDetail validateMeetingService(MeetingDetail meetingDetail) throws Exception {
        if (meetingDetail.getMeetingDetailId() <= 0)
            throw new Exception("Invalid meeting detail id");

        if (meetingDetail.getMeetingId() == null || meetingDetail.getMeetingId().isEmpty())
            throw new Exception("Invalid meeting id passed");

        var existingMeetingDetail = dbManager.getById(meetingDetail.getMeetingDetailId(), MeetingDetail.class);
        if (existingMeetingDetail == null)
            throw new Exception("Meeting detail not found");

        if (!meetingDetail.getMeetingId().equals(existingMeetingDetail.getMeetingId()))
            throw new Exception("Invalid meeting id");

        return existingMeetingDetail;
    }

    private String generatePassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    public MeetingDetail validateMeetingIdPassCodeService(MeetingDetail meetingDetail) throws Exception {
        if (meetingDetail.getMeetingPassword() == null || meetingDetail.getMeetingPassword().isEmpty())
            throw new Exception("Invalid meeting passcode");

        if (meetingDetail.getMeetingId() == null || meetingDetail.getMeetingId().isEmpty())
            throw new Exception("Invalid meeting id passed");

        var existingMeetingDetail = dbManager.getById(meetingDetail.getMeetingDetailId(), MeetingDetail.class);
        if (existingMeetingDetail == null)
            throw new Exception("Meeting detail not found");

        if (!existingMeetingDetail.getMeetingPassword().equals(meetingDetail.getMeetingPassword()))
            throw new Exception("Invalid meeting passcode. Please contact to admin");

        return existingMeetingDetail;
    }
}
