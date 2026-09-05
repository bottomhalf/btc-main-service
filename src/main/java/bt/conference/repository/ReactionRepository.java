package bt.conference.repository;

import bt.conference.entity.Reaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReactionRepository extends MongoRepository<Reaction, String> {

    List<Reaction> findByConversationId(String conversationId);

    List<Reaction> findByChatId(String chatId);

    List<Reaction> findByMessageIdIn(List<String> messageIds);

    long deleteByConversationId(String conversationId);

    long deleteByChatId(String chatId);

    long deleteByMessageIdIn(List<String> messageIds);
}
