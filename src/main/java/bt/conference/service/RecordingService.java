package bt.conference.service;

import bt.conference.config.LivekitConfig;
import bt.conference.serviceinterface.ILivekitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class RecordingService {
    @Autowired
    ILivekitService livekitService;
    @Autowired
    LivekitConfig livekitConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    public void startRoomRecording(String roomName) {

        String url = livekitConfig.url +
                        "/twirp/livekit.Egress/StartRoomCompositeEgress";

        String serverToken = livekitService.generateServerToken();

        Map<String, Object> payload = Map.of(
                "room_name", roomName,
                "layout", "grid",
                "file", Map.of(
                        "file_type", "MP4",
                        "file_name", roomName + "-" + System.currentTimeMillis()
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serverToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(
                url,
                new HttpEntity<>(payload, headers),
                String.class
        );
    }

    public void stopRoomRecording(String egressId) {

        String url =
                livekitConfig.url +
                        "/twirp/livekit.Egress/StopEgress";

        String serverToken = livekitService.generateServerToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serverToken);

        restTemplate.postForEntity(
                url,
                new HttpEntity<>(
                        Map.of("egress_id", egressId),
                        headers),
                String.class
        );
    }
}
