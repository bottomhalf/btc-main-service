package bt.conference.model;

import lombok.Data;

@Data
public class ParticipantTokenRequest {
    private String roomName;
    private String participantName;
    private boolean host;
}
