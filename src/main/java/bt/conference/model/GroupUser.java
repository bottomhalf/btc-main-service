package bt.conference.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupUser {
    public String avatar;
    public String conversationId;
    public String name;
    public String type;
    public String userId;
    public String designation;
}
