package bt.conference.controller;

import bt.conference.service.AuthenticateService;
import bt.conference.service.RecordingService;
import com.fierhub.model.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/recording")
public class RecordingController {
    @Autowired
    private RecordingService recordingService;
    @Autowired
    UserSession userSession;

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody Map<String, String> body) {
        recordingService.startRoomRecording(body.get("roomName"));
        return ResponseEntity.ok("Recording started");
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop(@RequestBody Map<String, String> body) {
        recordingService.stopRoomRecording(body.get("roomName"));
        return ResponseEntity.ok("Recording stopped");
    }
}
