package bt.conference.service;

import bt.conference.model.MessageSearchResult;
import bt.conference.repository.MessageSearchRepository;
import bt.conference.repository.MessageSearchRepository.MessageSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class MessageSearchService {

    private final MessageSearchRepository messageRepository;

    /**
     * Send a message
     */
    public MessageSearchResult searchMessagesService(String conId, int page, int limit) {
        if (!validateSearchCriteria(conId, page, limit)) {
            return MessageSearchResult.empty();
        }

        MessageSearchCriteria criteria = MessageSearchCriteria.builder()
                .conversationId(conId)
                .skip((page - 1) * limit)
                .limit(limit)
                .build();

        return this.messageRepository.searchMessages(criteria);
    }

    private boolean validateSearchCriteria(String conId, int page, int limit) {
        if (conId == null || conId.isEmpty()) {
            return false;
        }

        // At least one filter must be provided
        boolean hasFilter = page > 0 ||
                limit >= 10;

        if (!hasFilter) {
            throw new GlobalSearchException(GlobalSearchException.ErrorType.INVALID_INPUT,
                    "At least one search filter is required");
        }

        return true;
    }
}