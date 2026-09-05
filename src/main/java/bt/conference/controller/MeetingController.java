package bt.conference.controller;

import bt.conference.entity.MeetingDetail;
import bt.conference.serviceinterface.IMeetingService;
import com.fierhub.model.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/meeting/")
public class MeetingController {
    @Autowired
    IMeetingService _meetingService;

    @PostMapping("generateMeeting")
    public BaseResponse generateMeeting(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.generateMeetingService(meetingDetail);
        return BaseResponse.Ok(result);
    }

    @GetMapping("get-recent-meetings")
    public BaseResponse getRecentMeetings() throws Exception {
        var result = _meetingService.getRecentMeetingsService();
        return BaseResponse.Ok(result);
    }

    @PostMapping("generateQuickMeeting")
    public BaseResponse generateQuickMeeting(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.generateQuickMeetingService(meetingDetail);
        return BaseResponse.Ok(result);
    }

    @GetMapping("validateMeeting")
    public BaseResponse validateMeeting(@RequestParam(name = "access_token", required = false) String access_token,
                                        @RequestParam(name = "meetingId", required = false) String meetingId) throws Exception {
        if (access_token != null && !access_token.isEmpty() && !"undefined".equals(access_token)) {
            var result = _meetingService.validateMeetingService(access_token);
            return BaseResponse.Ok(result);
        } else if (meetingId != null && !meetingId.isEmpty() && !"undefined".equals(meetingId)) {
            var result = _meetingService.validateMeetingByIdService(meetingId);
            return BaseResponse.Ok(result);
        }
        throw new Exception("Please provide valid access_token or meetingId");
    }

    @GetMapping("validateMeetingById")
    public BaseResponse validateMeetingById(@RequestParam(name = "meetingId") String meetingId) throws Exception {
        var result = _meetingService.validateMeetingByIdService(meetingId);
        return BaseResponse.Ok(result);
    }

    @GetMapping("getAllMeetingByOrganizer")
    public BaseResponse getAllMeetingByOrganizer() throws Exception {
        var result = _meetingService.getAllMeetingByOrganizerService();
        return BaseResponse.Ok(result);
    }

    @PostMapping("validateMeetingIdPassCode")
    public BaseResponse validateMeetingIdPassCode(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.validateMeetingIdPassCodeService(meetingDetail);
        return BaseResponse.Ok(result);
    }

    @GetMapping("getAllScheduleMeetingByOrganizer")
    public BaseResponse getAllScheduleMeetingByOrganizer() throws Exception {
        var result = _meetingService.getAllScheduleMeetingByOrganizerService();
        return BaseResponse.Ok(result);
    }

    /**
     * Get meeting detail by meetingDetailId or meetingId
     * GET /api/meeting/getMeetingDetail?meetingDetailId=1 or ?meetingId=abc
     */
    @GetMapping("getMeetingDetail")
    public BaseResponse getMeetingDetail(
            @RequestParam(name = "meetingDetailId", required = false) Long meetingDetailId,
            @RequestParam(name = "meetingId", required = false) String meetingId
    ) throws Exception {
        var result = _meetingService.getMeetingDetailService(meetingDetailId, meetingId);
        return BaseResponse.Ok(result);
    }

    /**
     * Get meeting detail by ID in path
     * GET /api/meeting/getMeetingDetail/{meetingDetailId}
     */
    @GetMapping("getMeetingDetail/{meetingDetailId}")
    public BaseResponse getMeetingDetailById(
            @PathVariable("meetingDetailId") Long meetingDetailId
    ) throws Exception {
        var result = _meetingService.getMeetingDetailService(meetingDetailId, null);
        return BaseResponse.Ok(result);
    }

    /**
     * Edit / update meeting detail
     * PUT /api/meeting/updateMeetingDetail
     */
    @PutMapping("updateMeetingDetail")
    public BaseResponse updateMeetingDetail(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.updateMeetingDetailService(meetingDetail);
        return BaseResponse.Ok(result);
    }

    /**
     * Edit / update meeting detail (POST alternative)
     * POST /api/meeting/updateMeetingDetail
     */
    @PostMapping("updateMeetingDetail")
    public BaseResponse updateMeetingDetailPost(@RequestBody MeetingDetail meetingDetail) throws Exception {
        var result = _meetingService.updateMeetingDetailService(meetingDetail);
        return BaseResponse.Ok(result);
    }
}
