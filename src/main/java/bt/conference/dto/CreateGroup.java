package bt.conference.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateGroup {
    private String conversationName;
    private List<String> participantIds;
}