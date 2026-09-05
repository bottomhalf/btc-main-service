package bt.conference.controller;

import bt.conference.dto.PagedResponse;
import bt.conference.dto.RegisterUserRequest;
import bt.conference.dto.UpdateUserRequest;
import bt.conference.entity.Users;
import bt.conference.model.UsersSearchRequest;
import bt.conference.service.UsersService;
import com.fierhub.model.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/")
@RequiredArgsConstructor
public class UsersController {

    private final UsersService usersService;

    /**
     * Register a new user (persists to MySQL and MongoDB)
     * POST /api/users/register
     */
    @PostMapping("register")
    public BaseResponse registerUser(@RequestBody RegisterUserRequest request) throws Exception {
        Users registeredUser = usersService.registerUser(request);
        return BaseResponse.Ok(registeredUser);
    }

    /**
     * Update an existing user (updates both MySQL and MongoDB)
     * PUT /api/users/update
     */
    @PutMapping("update")
    public BaseResponse updateUser(@RequestBody UpdateUserRequest request) throws Exception {
        Users updatedUser = usersService.updateUser(request);
        return BaseResponse.Ok(updatedUser);
    }

    /**
     * Update an existing user by ID (updates both MySQL and MongoDB)
     * PUT /api/users/update/{id}
     */
    @PutMapping("update/{id}")
    public BaseResponse updateUserById(
            @PathVariable String id,
            @RequestBody UpdateUserRequest request
    ) throws Exception {
        request.setId(id);
        Users updatedUser = usersService.updateUser(request);
        return BaseResponse.Ok(updatedUser);
    }

    /**
     * Update an existing user (POST alternative)
     * POST /api/users/update
     */
    @PostMapping("update")
    public BaseResponse updateUserPost(@RequestBody UpdateUserRequest request) throws Exception {
        Users updatedUser = usersService.updateUser(request);
        return BaseResponse.Ok(updatedUser);
    }

    /**
     * Get all users with pagination
     * GET /api/users/get-users?pageNumber=1&pageSize=10
     */
    @GetMapping("get-users")
    public BaseResponse getAllUsers(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Users> response = usersService.getAllUsers(pageNumber, pageSize);
        return BaseResponse.Ok(response);
    }

    /**
     * Search users by username, email, firstName, lastName
     * GET /api/users/search?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search")
    public BaseResponse searchUsers(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Users> response = usersService.searchUsers(term, pageNumber, pageSize);
        return BaseResponse.Ok(response);
    }

    /**
     * Search users with advanced options (POST)
     * POST /api/users/search
     */
    @PostMapping("search")
    public BaseResponse searchUsersAdvanced(
            @RequestBody UsersSearchRequest request
    ) {
        PagedResponse<Users> response = usersService.searchUsers(request);
        return BaseResponse.Ok(response);
    }

    /**
     * Search only active users
     * GET /api/users/search/active?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search/active")
    public BaseResponse searchActiveUsers(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Users> response = usersService.searchActiveUsers(term, pageNumber, pageSize);
        return BaseResponse.Ok(response);
    }

    /**
     * Search users excluding current user (for chat)
     * GET /api/users/search/exclude/{userId}?term=john&pageNumber=1&pageSize=10
     */
    @GetMapping("search/exclude/{userId}")
    public BaseResponse searchUsersExcluding(
            @PathVariable String userId,
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PagedResponse<Users> response = usersService.searchUsersExcluding(
                userId, term, pageNumber, pageSize
        );
        return BaseResponse.Ok(response);
    }

    /**
     * Get single user by ID
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public BaseResponse getUserById(@PathVariable String id) {
        return usersService.getUserById(id)
                .map(BaseResponse::Ok)
                .orElse(BaseResponse.RaiseError("User not found", new Exception("User not found")));
    }

    /**
     * Get user by userId
     * GET /api/users/by-user-id/{userId}
     */
    @GetMapping("/by-user-id/{userId}")
    public BaseResponse getUserByUserId(@PathVariable String userId) {
        return usersService.getUserByUserId(userId)
                .map(BaseResponse::Ok)
                .orElse(BaseResponse.RaiseError("User not found", new Exception("User not found")));
    }
}
