package bt.conference.serviceinterface;

import bt.conference.dto.PagedResponse;
import bt.conference.entity.Conversation;
import bt.conference.entity.MeetingDetail;

import java.util.List;

public interface IMeetingService {
    PagedResponse<Conversation> generateMeetingService(MeetingDetail meetingDetail) throws Exception;
    PagedResponse<Conversation> getRecentMeetingsService() throws Exception;
    PagedResponse<Conversation> generateQuickMeetingService(MeetingDetail meetingDetail) throws Exception;
    Conversation validateMeetingService(String access_token) throws Exception;
    MeetingDetail validateMeetingIdPassCodeService(MeetingDetail meetingDetail) throws Exception;
}
