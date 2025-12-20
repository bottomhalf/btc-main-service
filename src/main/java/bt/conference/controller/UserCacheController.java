package bt.conference.controller;

import bt.conference.dto.PagedResponse;
import bt.conference.entity.UserCache;
import bt.conference.model.UserCacheSearchRequest;
import bt.conference.service.UserCacheService;
import in.bottomhalf.common.models.ApiErrorResponse;
import in.bottomhalf.common.models.ApiResponse;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse getAllUsers(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<UserCache> response = userCacheService.getAllUsers(pageNumber, pageSize);
        return ApiResponse.Ok(response);
    }

    /**
     * Search users by username, email, firstName, lastName
     * GET /api/users/search?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search")
    public ApiResponse searchUsers(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<UserCache> response = userCacheService.searchUsers(term, pageNumber, pageSize);
        return ApiResponse.Ok(response);
    }

    /**
     * Search users with advanced options (POST)
     * POST /api/users/search
     */
    @PostMapping("search")
    public ApiResponse searchUsersAdvanced(
            @RequestBody UserCacheSearchRequest request
    ) {
        PagedResponse<UserCache> response = userCacheService.searchUsers(request);
        return ApiResponse.Ok(response);
    }

    /**
     * Search only active users
     * GET /api/users/search/active?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search/active")
    public ApiResponse searchActiveUsers(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<UserCache> response = userCacheService.searchActiveUsers(term, pageNumber, pageSize);
        return ApiResponse.Ok(response);
    }

    /**
     * Search users excluding current user (for chat)
     * GET /api/users/search/exclude/{userId}?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search/exclude/{userId}")
    public ApiResponse searchUsersExcluding(
            @PathVariable String userId,
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<UserCache> response = userCacheService.searchUsersExcluding(
                userId, term, pageNumber, pageSize
        );
        return ApiResponse.Ok(response);
    }

    /**
     * Get single user by ID
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse getUserById(@PathVariable String id) {
        return userCacheService.getUserById(id)
                .map(ApiResponse::Ok)
                .orElse(ApiErrorResponse.BadRequest("User not found"));
    }

    /**
     * Get user by userId
     * GET /api/users/by-user-id/{userId}
     */
    @GetMapping("/by-user-id/{userId}")
    public ApiResponse getUserByUserId(@PathVariable String userId) {
        return userCacheService.getUserByUserId(userId)
                .map(ApiResponse::Ok)
                .orElse(ApiErrorResponse.BadRequest("User not found"));
    }
}