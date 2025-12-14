package bt.conference.repository;

import bt.conference.entity.UserCache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserCacheRepository extends MongoRepository<UserCache, String> {

    Optional<UserCache> findByUserId(String odUserId);

    Optional<UserCache> findByEmail(String email);

    Optional<UserCache> findByUsername(String username);

    @Query("{ $or: [ " +
            "  { 'username': { $regex: ?0, $options: 'i' } }, " +
            "  { 'email': { $regex: ?0, $options: 'i' } }, " +
            "  { 'first_name': { $regex: ?0, $options: 'i' } }, " +
            "  { 'last_name': { $regex: ?0, $options: 'i' } } " +
            "] }")
    Page<UserCache> searchUsers(String searchTerm, Pageable pageable);

    Page<UserCache> findByIsActiveTrue(Pageable pageable);
}