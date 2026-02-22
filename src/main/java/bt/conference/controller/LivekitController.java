package bt.conference.controller;

import java.util.Map;

import bt.conference.model.ParticipantTokenRequest;
import bt.conference.serviceinterface.ILivekitService;
import in.bottomhalf.common.models.ApiAuthResponse;
import in.bottomhalf.common.models.ApiErrorResponse;
import in.bottomhalf.common.models.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook.WebhookEvent;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/conference/")
public class LivekitController {
    @Autowired
    ILivekitService livekitService;

    /**
     * @return JSON object with the JWT token
     */
    @GetMapping(value = "check")
    public ApiResponse checkHealth() {
        return ApiResponse.Ok("Working");
    }

    /**
     * @param request object with roomName and participantName
     * @return JSON object with the JWT token
     */
    @PostMapping(value = "token")
    public ApiResponse createToken(@RequestBody ParticipantTokenRequest request) throws Exception {
        if (request.getRoomName() == null || request.getParticipantName() == null) {
            return ApiErrorResponse.BadRequest(Map.of("errorMessage", "roomName and participantName are required"));
        }

        var token = livekitService.createToken(request);
        return ApiAuthResponse.Ok(null, token);
    }
}
