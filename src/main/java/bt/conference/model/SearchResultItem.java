package bt.conference.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
     * Unique identifier of the entity
     */
    private String id;

    /**
     * Primary display text (name, title, etc.)
     */
    private String title;

    /**
     * Secondary display text (email, preview, etc.)
     */
    private String subtitle;

    /**
     * Avatar/icon URL
     */
    private String avatar;

    /**
     * Online status (for users)
     */
    private String status;

    /**
     * Relevance score (higher = more relevant)
     */
    private double score;

    /**
     * Highlighted matches (field -> highlighted text)
     */
    private Map<String, String> highlights;

    /**
     * Last activity/update timestamp (for sorting by recency)
     */
    private Instant lastActivity;

    /**
     * Additional metadata specific to the result type
     */
    private Map<String, Object> metadata;

    public enum ResultType {
        USER,
        CONVERSATION,
        MESSAGE,
        FILE,
        CHANNEL
    }
}