package bt.conference.service;

import bt.conference.dto.*;
import bt.conference.entity.UserCache;
import bt.conference.model.UserCacheSearchRequest;
import bt.conference.repository.UserCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final UserCacheRepository userCacheRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Get all users with pagination
     */
    public PagedResponse<UserCache> getAllUsers(int pageNumber, int pageSize) {

        Pageable pageable = PageRequest.of(
                pageNumber - 1,
                pageSize,
                Sort.by(Sort.Direction.ASC, "username")
        );

        Page<UserCache> page = userCacheRepository.findAll(pageable);

        return buildResponse(page, pageNumber, pageSize);
    }

    /**
     * Search users by username, email, firstName, lastName
     */
    public PagedResponse<UserCache> searchUsers(UserCacheSearchRequest request) {

        String searchTerm = request.getSearchTerm();
        int pageNumber = request.getPageNumber();
        int pageSize = request.getPageSize();
        int skip = (pageNumber - 1) * pageSize;

        // Build sort
        Sort.Direction direction = "DESC".equalsIgnoreCase(request.getSortDirection())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, request.getSortBy());

        Query query = new Query();

        // Add search criteria if term provided
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String pattern = searchTerm.trim();

            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("first_name").regex(pattern, "i"),
                    Criteria.where("last_name").regex(pattern, "i")
            );

            query.addCriteria(searchCriteria);
        }

        // Count total
        long totalRecords = mongoTemplate.count(query, UserCache.class);

        log.info("Search term: '{}', Total records: {}", searchTerm, totalRecords);

        // Add sorting and pagination
        query.with(sort);
        query.skip(skip);
        query.limit(pageSize);

        // Execute
        List<UserCache> users = mongoTemplate.find(query, UserCache.class);

        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

        return PagedResponse.of(
                users,
                totalPages,
                pageNumber,
                pageSize
        );
    }

    /**
     * Search users with simple parameters
     */
    public PagedResponse<UserCache> searchUsers(
            String searchTerm,
            int pageNumber,
            int pageSize
    ) {
        UserCacheSearchRequest request = new UserCacheSearchRequest();
        request.setSearchTerm(searchTerm);
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);

        return searchUsers(request);
    }

    /**
     * Search only active users
     */
    public PagedResponse<UserCache> searchActiveUsers(
            String searchTerm,
            int pageNumber,
            int pageSize
    ) {
        int skip = (pageNumber - 1) * pageSize;

        Query query = new Query();

        // Only active users
        query.addCriteria(Criteria.where("is_active").is(true));

        // Add search criteria
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String pattern = searchTerm.trim();

            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("first_name").regex(pattern, "i"),
                    Criteria.where("last_name").regex(pattern, "i")
            );

            query.addCriteria(searchCriteria);
        }

        long totalRecords = mongoTemplate.count(query, UserCache.class);

        query.with(Sort.by(Sort.Direction.ASC, "username"));
        query.skip(skip);
        query.limit(pageSize);

        List<UserCache> users = mongoTemplate.find(query, UserCache.class);

        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

        return PagedResponse.of(
                users,
                totalPages,
                pageNumber,
                pageSize
        );
    }

    /**
     * Search users excluding a specific user (useful for chat)
     */
    public PagedResponse<UserCache> searchUsersExcluding(
            String excludeUserId,
            String searchTerm,
            int pageNumber,
            int pageSize
    ) {
        int skip = (pageNumber - 1) * pageSize;

        Query query = new Query();

        // Exclude specific user
        query.addCriteria(Criteria.where("user_id").ne(excludeUserId));

        // Only active users
        query.addCriteria(Criteria.where("is_active").is(true));

        // Add search criteria
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String pattern = searchTerm.trim();

            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("first_name").regex(pattern, "i"),
                    Criteria.where("last_name").regex(pattern, "i")
            );

            query.addCriteria(searchCriteria);
        }

        long totalRecords = mongoTemplate.count(query, UserCache.class);

        query.with(Sort.by(Sort.Direction.ASC, "username"));
        query.skip(skip);
        query.limit(pageSize);

        List<UserCache> users = mongoTemplate.find(query, UserCache.class);

        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

        return PagedResponse.of(
                users,
                totalPages,
                pageNumber,
                pageSize
        );
    }

    /**
     * Get user by ID
     */
    public Optional<UserCache> getUserById(String id) {
        return userCacheRepository.findById(id);
    }

    /**
     * Get user by userId
     */
    public Optional<UserCache> getUserByUserId(String odUserId) {
        return userCacheRepository.findByUserId(odUserId);
    }

    /**
     * Get user by email
     */
    public Optional<UserCache> getUserByEmail(String email) {
        return userCacheRepository.findByEmail(email);
    }

    /**
     * Helper method to build PagedResponse from Page
     */
    private PagedResponse<UserCache> buildResponse(Page<UserCache> page, int pageNumber, int pageSize) {
        return PagedResponse.of(
                page.getContent(),
                page.getTotalPages(),
                pageNumber,
                pageSize
        );
    }
}