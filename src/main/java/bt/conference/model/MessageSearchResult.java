package bt.conference.model;

import bt.conference.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated search result wrapper for message queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSearchResult {

    /**
     * List of messages matching the search criteria
     */
    private List<Message> messages;

    /**
     * Total count of matching messages (for pagination)
     */
    private long totalCount;

    /**
     * Current page number (0-indexed)
     */
    private int page;

    /**
     * Page size (number of results per page)
     */
    private int pageSize;

    /**
     * Whether there are more results after this page
     */
    private boolean hasMore;

    /**
     * Search execution time in milliseconds (optional)
     */
    private Long searchTimeMs;

    /**
     * The search term used (optional, for debugging)
     */
    private String searchTerm;

    // ==================== Computed Properties ====================

    /**
     * Calculate total pages based on total count and page size.
     */
    public int getTotalPages() {
        if (pageSize <= 0) return 0;
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    /**
     * Check if there's a previous page.
     */
    public boolean hasPrevious() {
        return page > 0;
    }

    /**
     * Check if this is the first page.
     */
    public boolean isFirst() {
        return page == 0;
    }

    /**
     * Check if this is the last page.
     */
    public boolean isLast() {
        return !hasMore;
    }

    /**
     * Get the number of results in this page.
     */
    public int getNumberOfElements() {
        return messages != null ? messages.size() : 0;
    }

    /**
     * Check if results are empty.
     */
    public boolean isEmpty() {
        return messages == null || messages.isEmpty();
    }

    /**
     * Get the offset of the first element in this page.
     */
    public int getOffset() {
        return page * pageSize;
    }

    // ==================== Static Factory Methods ====================

    /**
     * Create an empty result.
     */
    public static MessageSearchResult empty() {
        return MessageSearchResult.builder()
                .messages(List.of())
                .totalCount(0)
                .page(0)
                .pageSize(0)
                .hasMore(false)
                .build();
    }

    /**
     * Create a result from a list with auto-calculated pagination.
     */
    public static MessageSearchResult of(List<Message> messages, long totalCount, int skip, int limit) {
        return MessageSearchResult.builder()
                .messages(messages)
                .totalCount(totalCount)
                .page(limit > 0 ? skip / limit : 0)
                .pageSize(limit)
                .hasMore(skip + messages.size() < totalCount)
                .build();
    }
}