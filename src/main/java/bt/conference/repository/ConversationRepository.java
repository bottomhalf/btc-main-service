package bt.conference.repository;

import bt.conference.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {
    List<Conversation> findByParticipantIdsContainingOrderByLastMessageSentAtDesc(String odUserId);

    // Find conversations where user is participant (with pagination)
    Page<Conversation> findByParticipantIdsContainingOrderByLastMessageAtDesc(
            String odUserId,
            Pageable pageable
    );

    // Search by username, email, or conversation name (with pagination)
    @Query("{ " +
            "'participant_ids': ?0, " +
            "'is_active': true, " +
            "$or: [ " +
            "  { 'conversation_name': { $regex: ?1, $options: 'i' } }, " +
            "  { 'participants.username': { $regex: ?1, $options: 'i' } }, " +
            "  { 'participants.email': { $regex: ?1, $options: 'i' } } " +
            "] " +
            "}")
    Page<Conversation> searchConversations(String odUserId, String searchTerm, Pageable pageable);

    // Count for search
    @Query(value = "{ " +
            "'participant_ids': ?0, " +
            "'is_active': true, " +
            "$or: [ " +
            "  { 'conversation_name': { $regex: ?1, $options: 'i' } }, " +
            "  { 'participants.username': { $regex: ?1, $options: 'i' } }, " +
            "  { 'participants.email': { $regex: ?1, $options: 'i' } } " +
            "] " +
            "}", count = true)
    long countSearchResults(String odUserId, String searchTerm);

    // Find direct conversation between two users
    @Query("{ 'conversation_type': 'direct', 'participant_ids': { $all: [?0, ?1] } }")
    Optional<Conversation> findDirectConversation(String odUserId1, String odUserId2);

    // Find direct conversation between two users
    @Query("{ 'conversation_id': '?0'}")
    Optional<Conversation> getMeetingById(String meetingId);
}
