package bt.conference.serviceinterface;

import bt.conference.dto.PagedResponse;
import bt.conference.entity.Conversation;
import bt.conference.entity.MeetingDetail;

import java.util.List;
import java.util.Map;

public interface IMeetingService {
    Map<String, Object> generateMeetingService(MeetingDetail meetingDetail) throws Exception;
    PagedResponse<Conversation> getRecentMeetingsService() throws Exception;
    Map<String, Object> generateQuickMeetingService(MeetingDetail meetingDetail) throws Exception;
    Conversation validateMeetingService(String access_token) throws Exception;
    MeetingDetail validateMeetingIdPassCodeService(MeetingDetail meetingDetail) throws Exception;
    Map<String, Object> getAllMeetingByOrganizerService() throws Exception;
    MeetingDetail validateMeetingByIdService(String meetingId) throws Exception;
    List<MeetingDetail> getAllScheduleMeetingByOrganizerService() throws Exception;
    MeetingDetail getMeetingDetailService(Long meetingDetailId, String meetingId) throws Exception;
    MeetingDetail updateMeetingDetailService(MeetingDetail meetingDetail) throws Exception;
}
