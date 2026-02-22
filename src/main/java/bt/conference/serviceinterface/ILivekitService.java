package bt.conference.serviceinterface;

import bt.conference.model.ParticipantTokenRequest;
import org.springframework.web.bind.annotation.RequestBody;

public interface ILivekitService {
    String createToken(@RequestBody ParticipantTokenRequest request) throws Exception;
    String generateServerToken();
}
