package bt.conference.controller;

import bt.conference.entity.MeetingDetail;
import bt.conference.serviceinterface.IMeetingService;
import in.bottomhalf.common.models.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/meeting/")
public class MeetingController {
    @Autowired
    IMeetingService _meetingService;

    @PostMapping("generateMeeting")
    public ApiResponse generateMeeting(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.generateMeetingService(meetingDetail);
        return ApiResponse.Ok(result);
    }

    @GetMapping("get-recent-meetings")
    public ApiResponse getRecentMeetings() throws Exception {
        var result = _meetingService.getRecentMeetingsService();
        return ApiResponse.Ok(result);
    }

    @PostMapping("generateQuickMeeting")
    public ApiResponse generateQuickMeeting(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.generateQuickMeetingService(meetingDetail);
        return ApiResponse.Ok(result);
    }

    @GetMapping("validateMeeting")
    public ApiResponse validateMeeting(@RequestParam(name = "access_token") String access_token) throws Exception {
        var result = _meetingService.validateMeetingService(access_token);
        return ApiResponse.Ok(result);
    }

    @PostMapping("validateMeetingIdPassCode")
    public ApiResponse validateMeetingIdPassCode(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.validateMeetingIdPassCodeService(meetingDetail);
        return ApiResponse.Ok(result);
    }
}
