package bt.conference.model;

import lombok.Data;

@Data
public class UserCacheSearchRequest {
    private String searchTerm;
    private int pageNumber = 1;
    private int pageSize = 10;
    private String sortBy = "username";
    private String sortDirection = "ASC";
}