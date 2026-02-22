package bt.conference.service;

import bt.conference.config.LivekitConfig;
import bt.conference.model.ParticipantTokenRequest;
import bt.conference.serviceinterface.ILivekitService;
import io.livekit.server.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

@Service
public class LivekitService implements ILivekitService {
    @Autowired
    LivekitConfig livekitConfig;

    public String createToken(@RequestBody ParticipantTokenRequest request) throws Exception {
        if (request.getRoomName() == null || request.getParticipantName() == null) {
            throw new Exception("roomName and participantName are required");
        }

        AccessToken token = new AccessToken(
                livekitConfig.getApiKey(),
                livekitConfig.getApiSecret()
        );

        token.setName(request.getParticipantName());
        token.setIdentity(request.getParticipantName());
        token.addGrants(new RoomJoin(true), new RoomName(request.getRoomName()));

        return token.toJwt();
    }

    public String generateServerToken() {
        AccessToken token = new AccessToken(
                livekitConfig.getApiKey(),
                livekitConfig.getApiSecret()
        );

        token.setIdentity("server-recording-service");

        token.addGrants(
                new RoomAdmin(true),
                new RoomRecord(true)
        );

        return token.toJwt();
    }
}
