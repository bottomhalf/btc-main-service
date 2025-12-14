package bt.conference.dto;

import bt.conference.entity.Conversation;
import bt.conference.entity.Message;
import lombok.Data;
import java.util.List;

@Data
public class ConversationWithMessages {
    private Conversation conversation;
    private List<Message> messages;
    private boolean isNew;
}