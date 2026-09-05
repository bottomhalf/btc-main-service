package bt.conference.service;

import bt.conference.dto.*;
import bt.conference.entity.Login;
import bt.conference.entity.LoginDetail;
import bt.conference.entity.UserDetail;
import bt.conference.entity.Users;
import bt.conference.model.UsersSearchRequest;
import bt.conference.repository.UsersRepository;
import com.fierhub.database.service.DbManager;
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

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersService {

    private final UsersRepository usersRepository;
    private final MongoTemplate mongoTemplate;
    private final DbManager dbManager;

    /**
     * Get all users with pagination
     */
    public PagedResponse<Users> getAllUsers(int pageNumber, int pageSize) {

        Pageable pageable = PageRequest.of(
                pageNumber - 1,
                pageSize,
                Sort.by(Sort.Direction.ASC, "username")
        );

        Page<Users> page = usersRepository.findAll(pageable);

        return buildResponse(page, pageNumber, pageSize);
    }

    /**
     * Search users by username, email, firstName, lastName
     */
    public PagedResponse<Users> searchUsers(UsersSearchRequest request) {

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
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i")
            );

            query.addCriteria(searchCriteria);
        }

        // Count total
        long totalRecords = mongoTemplate.count(query, Users.class);

        log.info("Search term: '{}', Total records: {}", searchTerm, totalRecords);

        // Add sorting and pagination
        query.with(sort);
        query.skip(skip);
        query.limit(pageSize);

        // Execute
        List<Users> users = mongoTemplate.find(query, Users.class);

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
    public PagedResponse<Users> searchUsers(
            String searchTerm,
            int pageNumber,
            int pageSize
    ) {
        UsersSearchRequest request = new UsersSearchRequest();
        request.setSearchTerm(searchTerm);
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);

        return searchUsers(request);
    }

    /**
     * Search only active users
     */
    public PagedResponse<Users> searchActiveUsers(
            String searchTerm,
            int pageNumber,
            int pageSize
    ) {
        int skip = (pageNumber - 1) * pageSize;

        Query query = new Query();

        // Only active users
        query.addCriteria(Criteria.where("status").is("ACTIVE"));

        // Add search criteria
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String pattern = searchTerm.trim();

            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i")
            );

            query.addCriteria(searchCriteria);
        }

        long totalRecords = mongoTemplate.count(query, Users.class);

        query.with(Sort.by(Sort.Direction.ASC, "username"));
        query.skip(skip);
        query.limit(pageSize);

        List<Users> users = mongoTemplate.find(query, Users.class);

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
    public PagedResponse<Users> searchUsersExcluding(
            String excludeUserId,
            String searchTerm,
            int pageNumber,
            int pageSize
    ) {
        int skip = (pageNumber - 1) * pageSize;

        Query query = new Query();

        // Exclude specific user
        query.addCriteria(Criteria.where("id").ne(excludeUserId));

        // Only active users
        query.addCriteria(Criteria.where("status").is("ACTIVE"));

        // Add search criteria
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String pattern = searchTerm.trim();

            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i")
            );

            query.addCriteria(searchCriteria);
        }

        long totalRecords = mongoTemplate.count(query, Users.class);

        query.with(Sort.by(Sort.Direction.ASC, "username"));
        query.skip(skip);
        query.limit(pageSize);

        List<Users> users = mongoTemplate.find(query, Users.class);

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
    public Optional<Users> getUserById(String id) {
        return usersRepository.findById(id);
    }

    /**
     * Get user by userId
     */
    public Optional<Users> getUserByUserId(String odUserId) {
        return usersRepository.findById(odUserId);
    }

    /**
     * Get user by email
     */
    public Optional<Users> getUserByEmail(String email) {
        return usersRepository.findByEmail(email);
    }

    /**
     * Helper method to build PagedResponse from Page
     */
    private PagedResponse<Users> buildResponse(Page<Users> page, int pageNumber, int pageSize) {
        return PagedResponse.of(
                page.getContent(),
                page.getTotalPages(),
                pageNumber,
                pageSize
        );
    }

    /**
     * Register a new user across both MySQL (UserDetail, Login) and MongoDB (Users)
     */
    public Users registerUser(RegisterUserRequest request) throws Exception {
        if (request == null) {
            throw new Exception("Request body cannot be null");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new Exception("Email is required for registration");
        }
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            throw new Exception("First name is required for registration");
        }

        String email = request.getEmail().trim().toLowerCase();

        // 1. Check if user already exists in MongoDB
        Optional<Users> existingMongoUser = usersRepository.findByEmail(email);
        if (existingMongoUser.isPresent()) {
            throw new Exception("User already exists with email: " + email);
        }

        Date utilDate = new Date();
        Timestamp timestamp = new Timestamp(utilDate.getTime());

        // 2. MySQL: Create and Save UserDetail
        long nextUserId = dbManager.nextLongPrimaryKey(UserDetail.class);
        UserDetail userDetail = UserDetail.builder()
                .userId(nextUserId)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName() != null ? request.getLastName().trim() : "")
                .mobile(request.getMobile() != null ? request.getMobile().trim() : "")
                .email(email)
                .pinCode(0)
                .roleId(1)
                .isActive(true)
                .createdBy(0L)
                .updatedBy(0L)
                .createdOn(timestamp)
                .updatedOn(timestamp)
                .gender("m")
                .maritalStatus(true)
                .build();

        dbManager.save(userDetail);
        log.info("Saved UserDetail to MySQL with userId: {}", nextUserId);

        // 3. MySQL: Create and Save Login record
        String code = "BOT";
        String password = (request.getPassword() != null && !request.getPassword().trim().isEmpty())
                ? request.getPassword().trim()
                : generateRandomPassword(8);

        long nextLoginId = dbManager.nextLongPrimaryKey(LoginDetail.class);
        Login login = Login.builder()
                .loginId(nextLoginId)
                .userId(nextUserId)
                .createdBy(0L)
                .updatedBy(0L)
                .code(code)
                .email(email)
                .password(password)
                .roleId(0)
                .isAccountConfig(true)
                .isActive(true)
                .createdOn(timestamp)
                .updatedOn(timestamp)
                .build();

        dbManager.save(login);
        log.info("Saved Login to MySQL with loginId: {}", nextLoginId);

        // 4. MongoDB: Create and Save Users document
        String formattedUserId = String.format("%s%05d", code, nextUserId);
        Instant now = Instant.now();

        String username;
        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            username = request.getUsername().trim();
        } else if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
            username = request.getFirstName().trim() + "_" + request.getLastName().trim();
        } else {
            username = request.getFirstName().trim();
        }

        Users mongoUser = Users.builder()
                .id(formattedUserId)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName() != null ? request.getLastName().trim() : "")
                .email(email)
                .createdAt(now)
                .updatedAt(now)
                .status("ACTIVE")
                .avatarUrl(request.getAvatarUrl() != null ? request.getAvatarUrl() : "")
                .username(username)
                .build();

        Users savedMongoUser = usersRepository.save(mongoUser);
        log.info("Saved Users to MongoDB with id: {}", formattedUserId);

        return savedMongoUser;
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Update an existing user across both MySQL (UserDetail, Login) and MongoDB (Users)
     */
    public Users updateUser(UpdateUserRequest request) throws Exception {
        if (request == null) {
            throw new Exception("Request body cannot be null");
        }

        // 1. Identify and fetch MongoDB user
        Users mongoUser = null;
        if (request.getId() != null && !request.getId().trim().isEmpty()) {
            mongoUser = usersRepository.findById(request.getId().trim()).orElse(null);
        }
        if (mongoUser == null && request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            mongoUser = usersRepository.findByEmail(request.getEmail().trim().toLowerCase()).orElse(null);
        }
        if (mongoUser == null && request.getUserId() != null && request.getUserId() > 0) {
            String formattedId = String.format("BOT%05d", request.getUserId());
            mongoUser = usersRepository.findById(formattedId).orElse(null);
        }

        // 2. Determine numeric MySQL userId
        long numericUserId = 0L;
        if (request.getUserId() != null && request.getUserId() > 0) {
            numericUserId = request.getUserId();
        } else if (mongoUser != null && mongoUser.getId() != null) {
            try {
                numericUserId = UtilService.extractEmployeeId(mongoUser.getId(), "BOT");
            } catch (Exception e) {
                String digits = mongoUser.getId().replaceAll("\\D+", "");
                if (!digits.isEmpty()) {
                    numericUserId = Long.parseLong(digits);
                }
            }
        }

        // 3. Fetch existing MySQL UserDetail
        UserDetail existingUserDetail = null;
        if (numericUserId > 0) {
            existingUserDetail = dbManager.getById(numericUserId, UserDetail.class);
        }
        if (existingUserDetail == null && request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            existingUserDetail = dbManager.queryRaw("select * from users where email = '" + request.getEmail().trim() + "'", UserDetail.class);
            if (existingUserDetail != null) {
                numericUserId = existingUserDetail.getUserId();
            }
        }
        if (existingUserDetail == null && mongoUser != null && mongoUser.getEmail() != null) {
            existingUserDetail = dbManager.queryRaw("select * from users where email = '" + mongoUser.getEmail() + "'", UserDetail.class);
            if (existingUserDetail != null) {
                numericUserId = existingUserDetail.getUserId();
            }
        }

        if (mongoUser == null && existingUserDetail == null) {
            throw new Exception("User not found. Please provide a valid id, userId, or email.");
        }

        Date utilDate = new Date();
        Timestamp timestamp = new Timestamp(utilDate.getTime());

        // 4. Update MySQL UserDetail
        if (existingUserDetail != null) {
            if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
                existingUserDetail.setFirstName(request.getFirstName().trim());
            }
            if (request.getLastName() != null) {
                existingUserDetail.setLastName(request.getLastName().trim());
            }
            if (request.getMobile() != null) {
                existingUserDetail.setMobile(request.getMobile().trim());
            }
            if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                existingUserDetail.setEmail(request.getEmail().trim().toLowerCase());
            }
            if (request.getAvatarUrl() != null) {
                existingUserDetail.setImageURL(request.getAvatarUrl());
            }
            if (request.getAddress() != null) {
                existingUserDetail.setAddress(request.getAddress());
            }
            if (request.getCity() != null) {
                existingUserDetail.setCity(request.getCity());
            }
            if (request.getState() != null) {
                existingUserDetail.setState(request.getState());
            }
            if (request.getCountry() != null) {
                existingUserDetail.setCountry(request.getCountry());
            }
            if (request.getPinCode() != null) {
                existingUserDetail.setPinCode(request.getPinCode());
            }
            if (request.getGender() != null) {
                existingUserDetail.setGender(request.getGender());
            }
            if (request.getIsActive() != null) {
                existingUserDetail.setActive(request.getIsActive());
            } else if (request.getStatus() != null) {
                existingUserDetail.setActive("ACTIVE".equalsIgnoreCase(request.getStatus()));
            }
            existingUserDetail.setUpdatedOn(timestamp);

            dbManager.save(existingUserDetail);
            log.info("Updated UserDetail in MySQL for userId: {}", existingUserDetail.getUserId());
        }

        // 5. Update MySQL Login record (if credentials, contact or status changed)
        if (numericUserId > 0) {
            Login existingLogin = dbManager.queryRaw("select * from login where userId = " + numericUserId, Login.class);
            if (existingLogin != null) {
                boolean loginChanged = false;
                if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                    existingLogin.setEmail(request.getEmail().trim().toLowerCase());
                    loginChanged = true;
                }
                if (request.getMobile() != null) {
                    existingLogin.setMobile(request.getMobile().trim());
                    loginChanged = true;
                }
                if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
                    existingLogin.setPassword(request.getPassword().trim());
                    loginChanged = true;
                }
                if (request.getIsActive() != null) {
                    existingLogin.setIsActive(request.getIsActive());
                    loginChanged = true;
                } else if (request.getStatus() != null) {
                    existingLogin.setIsActive("ACTIVE".equalsIgnoreCase(request.getStatus()));
                    loginChanged = true;
                }

                if (loginChanged) {
                    existingLogin.setUpdatedOn(timestamp);
                    dbManager.save(existingLogin);
                    log.info("Updated Login in MySQL for userId: {}", numericUserId);
                }
            }
        }

        // 6. Update MongoDB Users document
        if (mongoUser != null) {
            if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
                mongoUser.setFirstName(request.getFirstName().trim());
            }
            if (request.getLastName() != null) {
                mongoUser.setLastName(request.getLastName().trim());
            }
            if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                mongoUser.setEmail(request.getEmail().trim().toLowerCase());
            }
            if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
                mongoUser.setUsername(request.getUsername().trim());
            } else if (request.getFirstName() != null || request.getLastName() != null) {
                String first = mongoUser.getFirstName() != null ? mongoUser.getFirstName() : "";
                String last = mongoUser.getLastName() != null ? mongoUser.getLastName() : "";
                if (!last.isEmpty()) {
                    mongoUser.setUsername(first + "_" + last);
                } else {
                    mongoUser.setUsername(first);
                }
            }
            if (request.getAvatarUrl() != null) {
                mongoUser.setAvatarUrl(request.getAvatarUrl());
            }
            if (request.getStatus() != null) {
                mongoUser.setStatus(request.getStatus());
            } else if (request.getIsActive() != null) {
                mongoUser.setStatus(request.getIsActive() ? "ACTIVE" : "INACTIVE");
            }
            mongoUser.setUpdatedAt(Instant.now());

            Users updatedMongoUser = usersRepository.save(mongoUser);
            log.info("Updated Users in MongoDB for id: {}", updatedMongoUser.getId());
            return updatedMongoUser;
        }

        // Fallback: If user existed only in MySQL, sync/create into MongoDB
        if (existingUserDetail != null) {
            String formattedUserId = String.format("BOT%05d", existingUserDetail.getUserId());
            Instant now = Instant.now();
            String username = existingUserDetail.getLastName() != null && !existingUserDetail.getLastName().isEmpty()
                    ? existingUserDetail.getFirstName() + "_" + existingUserDetail.getLastName()
                    : existingUserDetail.getFirstName();

            Users newMongoUser = Users.builder()
                    .id(formattedUserId)
                    .firstName(existingUserDetail.getFirstName())
                    .lastName(existingUserDetail.getLastName())
                    .email(existingUserDetail.getEmail())
                    .createdAt(now)
                    .updatedAt(now)
                    .status(existingUserDetail.isActive() ? "ACTIVE" : "INACTIVE")
                    .avatarUrl(existingUserDetail.getImageURL() != null ? existingUserDetail.getImageURL() : "")
                    .username(username)
                    .build();

            return usersRepository.save(newMongoUser);
        }

        throw new Exception("Failed to update user");
    }
}
