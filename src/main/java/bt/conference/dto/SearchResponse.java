package bt.conference.dto;

import bt.conference.entity.Conversation;
import bt.conference.entity.UserCache;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponse {
    private List<Conversation> conversations;
    private List<UserCache> newUsers;  // Users without existing direct chat
}