package bt.conference.dto;

import lombok.Data;

@Data
public class SearchRequest {
    private String searchTerm;
    private int pageNumber = 1;
    private int pageSize = 10;
}