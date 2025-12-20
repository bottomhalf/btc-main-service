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

    @GetMapping("getAllMeetingByOrganizer")
    public ApiResponse getAllMeetingByOrganizer() throws Exception {
        var result = _meetingService.getAllMeetingByOrganizerService();
        return ApiResponse.Ok(result);
    }

    @PostMapping("generateQuickMeeting")
    public ApiResponse generateQuickMeeting(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.generateQuickMeetingService(meetingDetail);
        return ApiResponse.Ok(result);
    }

    @PostMapping("validateMeeting")
    public ApiResponse validateMeeting(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.validateMeetingService(meetingDetail);
        return ApiResponse.Ok(result);
    }

    @PostMapping("validateMeetingIdPassCode")
    public ApiResponse validateMeetingIdPassCode(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.validateMeetingIdPassCodeService(meetingDetail);
        return ApiResponse.Ok(result);
    }
}
