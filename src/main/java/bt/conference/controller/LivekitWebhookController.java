package bt.conference.controller;

import bt.conference.config.LivekitConfig;
import bt.conference.service.RecordingService;
import in.bottomhalf.common.models.ApiResponse;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@AllArgsConstructor
@RestController
@RequestMapping("/api/livekit")
public class LivekitWebhookController {
    private final RecordingService recordingService;
    private final LivekitConfig livekitConfig;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody Map<String, Object> payload) {

        String event = (String) payload.get("event");

        if ("room_finished".equals(event)) {
            Map<String, Object> room =
                    (Map<String, Object>) payload.get("room");

            String roomName = (String) room.get("name");

            // Safety: stop recording if still running
            recordingService.stopRoomRecording(roomName);
        }

        return ResponseEntity.ok().build();
    }


    @PostMapping(value = "livekit/webhook", consumes = "application/webhook+json")
    public ApiResponse receiveWebhook(@RequestHeader("Authorization") String authHeader, @RequestBody String body) {
        WebhookReceiver webhookReceiver = new WebhookReceiver(
                livekitConfig.getApiKey(),
                livekitConfig.getApiSecret()
        );

        try {
            LivekitWebhook.WebhookEvent event = webhookReceiver.receive(body, authHeader);
            System.out.println("LiveKit Webhook: " + event.toString());
        } catch (Exception e) {
            System.err.println("Error validating webhook event: " + e.getMessage());
        }
        return ApiResponse.Ok("ok");
    }
}