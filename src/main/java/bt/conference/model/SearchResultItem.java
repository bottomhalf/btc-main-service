package bt.conference.model;

import bt.conference.entity.Conversation;
import bt.conference.entity.UserCache;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Unified search result item that can represent any searchable entity.
 * This provides a consistent structure for the frontend to render results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResultItem {

    /**
     * Type of result: USER, CONVERSATION, MESSAGE, FILE, CHANNEL
     */
    private ResultType type;

    /**
     * UserCache entity
     */
    private List<UserCache> userCache;

    /**
     * Conversation entity
     */
    private List<Conversation> conversation;

    /**
     * Last activity/update timestamp (for sorting by recency)
     */
    private Instant lastActivity;

    /**
     * Search metadata
     */
    private SearchMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchMetadata {
        private String searchTerm;
        private long totalCount;
        private int page;
        private int limit;
        private long executionTimeMs;
        private boolean fromCache;
        private boolean isTypeahead;
        private Map<String, Integer> countByType;
    }

    public enum ResultType {
        USER,
        CONVERSATION,
        MESSAGE,
        FILE,
        CHANNEL
    }
}