package bt.conference.model;

import bt.conference.entity.Conversation;
import bt.conference.entity.Message;
import bt.conference.entity.UserCache;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response structure for global search, optimized for Teams-like UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlobalSearchResponse {

    /**
     * Grouped results by type (for tabbed UI: People, Chats, Messages, Files)
     */
    private GroupedResults results;

    /**
     * Search metadata
     */
    private SearchMetadata metadata;

    /**
     * Error information (if any)
     */
    private ErrorInfo error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupedResults {
        private List<UserCache> users;
        private List<Conversation> conversations;
        private List<Message> messages;
        private List<SearchResultItem> files;

        private int userCount;
        private int conversationCount;
        private int messageCount;
        private int fileCount;
    }

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
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String code;
        private String message;
    }

    public boolean hasResults() {
        if (results != null) {
            return (results.users != null && !results.users.isEmpty())
                    || (results.conversations != null && !results.conversations.isEmpty())
                    || (results.messages != null && !results.messages.isEmpty())
                    || (results.files != null && !results.files.isEmpty());
        }
        return false;
    }

    public boolean hasError() {
        return error != null;
    }

    public static GlobalSearchResponse error(String code, String message) {
        return GlobalSearchResponse.builder()
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }
}