package bt.conference.dto;

import lombok.Data;

@Data
public class SendMessage {
    private String conversationId;
    private String content;
    private String messageType;
}