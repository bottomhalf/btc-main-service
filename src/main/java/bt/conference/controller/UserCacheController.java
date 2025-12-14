package bt.conference.controller;

import bt.conference.dto.PagedResponse;
import bt.conference.entity.UserCache;
import bt.conference.model.UserCacheSearchRequest;
import bt.conference.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user-cache/")
@RequiredArgsConstructor
public class UserCacheController {

    private final UserCacheService userCacheService;

    /**
     * Get all users with pagination
     * GET /api/users?pageNumber=1&pageSize=10
     */
    @GetMapping("get-users")
    public ResponseEntity<PagedResponse<UserCache>> getAllUsers(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<UserCache> response = userCacheService.getAllUsers(pageNumber, pageSize);
        return ResponseEntity.ok(response);
    }

    /**
     * Search users by username, email, firstName, lastName
     * GET /api/users/search?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search")
    public ResponseEntity<PagedResponse<UserCache>> searchUsers(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<UserCache> response = userCacheService.searchUsers(term, pageNumber, pageSize);
        return ResponseEntity.ok(response);
    }

    /**
     * Search users with advanced options (POST)
     * POST /api/users/search
     */
    @PostMapping("search")
    public ResponseEntity<PagedResponse<UserCache>> searchUsersAdvanced(
            @RequestBody UserCacheSearchRequest request
    ) {
        PagedResponse<UserCache> response = userCacheService.searchUsers(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Search only active users
     * GET /api/users/search/active?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search/active")
    public ResponseEntity<PagedResponse<UserCache>> searchActiveUsers(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<UserCache> response = userCacheService.searchActiveUsers(term, pageNumber, pageSize);
        return ResponseEntity.ok(response);
    }

    /**
     * Search users excluding current user (for chat)
     * GET /api/users/search/exclude/{userId}?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search/exclude/{userId}")
    public ResponseEntity<PagedResponse<UserCache>> searchUsersExcluding(
            @PathVariable String userId,
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<UserCache> response = userCacheService.searchUsersExcluding(
                userId, term, pageNumber, pageSize
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Get single user by ID
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserCache> getUserById(@PathVariable String id) {
        return userCacheService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get user by userId
     * GET /api/users/by-user-id/{userId}
     */
    @GetMapping("/by-user-id/{userId}")
    public ResponseEntity<UserCache> getUserByUserId(@PathVariable String userId) {
        return userCacheService.getUserByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}