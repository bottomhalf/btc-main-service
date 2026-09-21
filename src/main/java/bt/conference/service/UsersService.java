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
import java.util.ArrayList;
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
     * Register a new user across MySQL (UserDetail, Login) and MongoDB (Users).
     * Mandatory: inserts into 3 tables/collections:
     *   1) user / users in MySQL (UserDetail)
     *   2) login in MySQL (Login)
     *   3) users in MongoDB (Users)
     * If failed or missing in any table, revert/rollback all.
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

        // 1. Check if user already exists across MongoDB and MySQL
        Optional<Users> existingMongoUser = usersRepository.findByEmail(email);
        if (existingMongoUser.isPresent()) {
            throw new Exception("User already exists with email: " + email);
        }
        if (findUserDetailByEmail(email) != null) {
            throw new Exception("User already exists in MySQL with email: " + email);
        }
        if (findLoginByEmail(email) != null) {
            throw new Exception("Login already exists in MySQL with email: " + email);
        }

        Date utilDate = new Date();
        Timestamp timestamp = new Timestamp(utilDate.getTime());
        Instant now = Instant.now();

        long nextUserId = dbManager.nextLongPrimaryKey(UserDetail.class);
        String code = "BOT";
        String formattedUserId = String.format("%s%05d", code, nextUserId);
        long nextLoginId = dbManager.nextLongPrimaryKey(LoginDetail.class);

        boolean mongoMutated = false;
        boolean userDetailMutated = false;
        boolean loginMutated = false;

        try {
            // 2. MySQL: Create and Save UserDetail (users table)
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
            userDetailMutated = true;
            log.info("Saved UserDetail to MySQL with userId: {}", nextUserId);

            // 3. MySQL: Create and Save Login record (login table)
            String password = (request.getPassword() != null && !request.getPassword().trim().isEmpty())
                    ? request.getPassword().trim()
                    : generateRandomPassword(8);

            Login login = Login.builder()
                    .loginId(nextLoginId)
                    .userId(nextUserId)
                    .createdBy(0L)
                    .updatedBy(0L)
                    .code(code)
                    .email(email)
                    .mobile(request.getMobile() != null ? request.getMobile().trim() : "")
                    .password(password)
                    .roleId(0)
                    .isAccountConfig(true)
                    .isActive(true)
                    .createdOn(timestamp)
                    .updatedOn(timestamp)
                    .build();

            dbManager.save(login);
            loginMutated = true;
            log.info("Saved Login to MySQL with loginId: {}", nextLoginId);

            // 4. MongoDB: Create and Save Users document (users collection)
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
            mongoMutated = true;
            log.info("Saved Users to MongoDB with id: {}", formattedUserId);

            // 5. Verification of mandatory records in all 3 tables: login, user (MySQL), users (MongoDB)
            Users verifiedMongoUser = usersRepository.findById(formattedUserId).orElse(null);
            UserDetail verifiedUserDetail = findUserDetailById(nextUserId);
            Login verifiedLogin = findLoginByUserId(nextUserId);

            if (verifiedMongoUser == null || verifiedUserDetail == null || verifiedLogin == null) {
                log.error("Mandatory record verification failed after registration! MongoDB users: {}, MySQL user: {}, MySQL login: {}",
                        verifiedMongoUser != null, verifiedUserDetail != null, verifiedLogin != null);

                rollbackUserChanges(
                        mongoMutated, null, formattedUserId,
                        userDetailMutated, null, nextUserId,
                        loginMutated, null, nextLoginId
                );

                List<String> missingTables = new ArrayList<>();
                if (verifiedLogin == null) missingTables.add("login (in MySQL)");
                if (verifiedUserDetail == null) missingTables.add("user (in MySQL)");
                if (verifiedMongoUser == null) missingTables.add("users (in MongoDB)");

                throw new Exception("Registration failed: Mandatory record missing in table(s): " + String.join(", ", missingTables) + ". All changes have been reverted.");
            }

            return savedMongoUser;
        } catch (Exception e) {
            log.error("Error during registration, reverting all changes: {}", e.getMessage(), e);
            rollbackUserChanges(
                    mongoMutated, null, formattedUserId,
                    userDetailMutated, null, nextUserId,
                    loginMutated, null, nextLoginId
            );
            throw new Exception("Error during registration (all changes reverted): " + e.getMessage());
        }
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

    private long extractNumericUserId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return 0L;
        }
        try {
            return UtilService.extractEmployeeId(id.trim(), "BOT");
        } catch (Exception e) {
            String digits = id.replaceAll("\\D+", "");
            if (!digits.isEmpty()) {
                try {
                    return Long.parseLong(digits);
                } catch (NumberFormatException ex) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private UserDetail findUserDetailById(long userId) {
        if (userId <= 0) {
            return null;
        }
        try {
            List<UserDetail> list = dbManager.queryList("select * from users where userId = " + userId, UserDetail.class);
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
        } catch (Exception e) {
            log.debug("Error querying user by userId via queryList: {}", e.getMessage());
        }
        try {
            return dbManager.getById(userId, UserDetail.class);
        } catch (Exception e) {
            log.debug("UserDetail not found by userId {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private UserDetail findUserDetailByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        String sanitizedEmail = email.trim().replace("'", "''");
        try {
            List<UserDetail> list = dbManager.queryList("select * from users where email = '" + sanitizedEmail + "'", UserDetail.class);
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
        } catch (Exception e) {
            log.debug("Error querying user by email via queryList: {}", e.getMessage());
        }
        try {
            return dbManager.queryRaw("select * from users where email = '" + sanitizedEmail + "'", UserDetail.class);
        } catch (Exception e) {
            log.debug("UserDetail not found by email {}: {}", email, e.getMessage());
        }
        return null;
    }

    private Login findLoginByUserId(long userId) {
        if (userId <= 0) {
            return null;
        }
        try {
            List<Login> list = dbManager.queryList("select * from login where userId = " + userId, Login.class);
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
        } catch (Exception e) {
            log.debug("Error querying login by userId via queryList: {}", e.getMessage());
        }
        try {
            return dbManager.queryRaw("select * from login where userId = " + userId, Login.class);
        } catch (Exception e) {
            log.debug("Login not found by userId {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private Login findLoginByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        String sanitizedEmail = email.trim().replace("'", "''");
        try {
            List<Login> list = dbManager.queryList("select * from login where email = '" + sanitizedEmail + "'", Login.class);
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
        } catch (Exception e) {
            log.debug("Error querying login by email via queryList: {}", e.getMessage());
        }
        try {
            return dbManager.queryRaw("select * from login where email = '" + sanitizedEmail + "'", Login.class);
        } catch (Exception e) {
            log.debug("Login not found by email {}: {}", email, e.getMessage());
        }
        return null;
    }

    private Users cloneMongoUser(Users source) {
        if (source == null) {
            return null;
        }
        return Users.builder()
                .id(source.getId())
                .avatarUrl(source.getAvatarUrl())
                .createdAt(source.getCreatedAt())
                .email(source.getEmail())
                .firstName(source.getFirstName())
                .lastName(source.getLastName())
                .status(source.getStatus())
                .updatedAt(source.getUpdatedAt())
                .username(source.getUsername())
                .build();
    }

    private UserDetail cloneUserDetail(UserDetail source) {
        if (source == null) {
            return null;
        }
        return UserDetail.builder()
                .userId(source.getUserId())
                .firstName(source.getFirstName())
                .lastName(source.getLastName())
                .fatherName(source.getFatherName())
                .motherName(source.getMotherName())
                .email(source.getEmail())
                .mobile(source.getMobile())
                .alternateNumber(source.getAlternateNumber())
                .address(source.getAddress())
                .city(source.getCity())
                .pinCode(source.getPinCode())
                .state(source.getState())
                .country(source.getCountry())
                .roleId(source.getRoleId())
                .isActive(source.isActive())
                .imageURL(source.getImageURL())
                .createdBy(source.getCreatedBy())
                .updatedBy(source.getUpdatedBy())
                .createdOn(source.getCreatedOn())
                .updatedOn(source.getUpdatedOn())
                .dateOfBirth(source.getDateOfBirth())
                .gender(source.getGender())
                .maritalStatus(source.isMaritalStatus())
                .religionId(source.getReligionId())
                .nationality(source.getNationality())
                .build();
    }

    private Login cloneLogin(Login source) {
        if (source == null) {
            return null;
        }
        return Login.builder()
                .loginId(source.getLoginId())
                .userId(source.getUserId())
                .email(source.getEmail())
                .mobile(source.getMobile())
                .password(source.getPassword())
                .deviceId(source.getDeviceId())
                .roleId(source.getRoleId())
                .isAccountConfig(source.getIsAccountConfig())
                .isActive(source.getIsActive())
                .refreshToken(source.getRefreshToken())
                .code(source.getCode())
                .createdBy(source.getCreatedBy())
                .updatedBy(source.getUpdatedBy())
                .createdOn(source.getCreatedOn())
                .updatedOn(source.getUpdatedOn())
                .build();
    }

    private void rollbackUserChanges(
            boolean mongoMutated, Users originalMongoUser, String savedMongoId,
            boolean userDetailMutated, UserDetail originalUserDetail, long numericUserId,
            boolean loginMutated, Login originalLogin, Long savedLoginId
    ) {
        log.warn("Rolling back user changes across MongoDB and MySQL...");

        // 1. Rollback MySQL login first (respecting potential foreign key constraints)
        if (loginMutated) {
            try {
                if (originalLogin != null) {
                    dbManager.save(originalLogin);
                    log.info("Rolled back MySQL Login to original state for loginId: {}", originalLogin.getLoginId());
                } else if (savedLoginId != null && savedLoginId > 0) {
                    dbManager.execute("delete from login where loginId = " + savedLoginId);
                    log.info("Deleted newly created MySQL Login with loginId: {}", savedLoginId);
                } else if (numericUserId > 0) {
                    dbManager.execute("delete from login where userId = " + numericUserId);
                    log.info("Deleted newly created MySQL Login for userId: {}", numericUserId);
                }
            } catch (Exception ex) {
                log.error("Failed to rollback MySQL Login: {}", ex.getMessage(), ex);
            }
        }

        // 2. Rollback MySQL users (UserDetail)
        if (userDetailMutated) {
            try {
                if (originalUserDetail != null) {
                    dbManager.save(originalUserDetail);
                    log.info("Rolled back MySQL UserDetail to original state for userId: {}", originalUserDetail.getUserId());
                } else if (numericUserId > 0) {
                    dbManager.execute("delete from users where userId = " + numericUserId);
                    log.info("Deleted newly created MySQL UserDetail with userId: {}", numericUserId);
                }
            } catch (Exception ex) {
                log.error("Failed to rollback MySQL UserDetail: {}", ex.getMessage(), ex);
            }
        }

        // 3. Rollback MongoDB users collection
        if (mongoMutated) {
            try {
                if (originalMongoUser != null) {
                    usersRepository.save(originalMongoUser);
                    log.info("Rolled back MongoDB Users to original state for id: {}", originalMongoUser.getId());
                } else if (savedMongoId != null) {
                    usersRepository.deleteById(savedMongoId);
                    log.info("Deleted newly created MongoDB Users with id: {}", savedMongoId);
                }
            } catch (Exception ex) {
                log.error("Failed to rollback MongoDB user: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Update an existing user across both MySQL (UserDetail, Login) and MongoDB (Users).
     * If user does not exist in MongoDB, create it; otherwise update.
     * If user does not exist in MySQL, create it; otherwise update.
     * Ensures MongoDB and MySQL are always in sync.
     * Mandatory: records must exist/be updated in all 3 tables:
     *   1) MongoDB: users collection
     *   2) MySQL: users table (UserDetail)
     *   3) MySQL: login table (Login)
     * If in any one table the record is missing, undo/rollback everything.
     */
    public Users updateUser(UpdateUserRequest request) throws Exception {
        if (request == null) {
            throw new Exception("Request body cannot be null");
        }

        String reqId = (request.getId() != null && !request.getId().trim().isEmpty()) ? request.getId().trim() : null;
        String reqEmail = (request.getEmail() != null && !request.getEmail().trim().isEmpty()) ? request.getEmail().trim().toLowerCase() : null;
        Long reqUserId = (request.getUserId() != null && request.getUserId() > 0) ? request.getUserId() : null;

        long numericUserId = 0L;
        if (reqUserId != null) {
            numericUserId = reqUserId;
        } else if (reqId != null) {
            numericUserId = extractNumericUserId(reqId);
        }

        // 1. Check MongoDB user
        Users mongoUser = null;
        if (reqId != null) {
            mongoUser = usersRepository.findById(reqId).orElse(null);
        }
        if (mongoUser == null && reqEmail != null) {
            mongoUser = usersRepository.findByEmail(reqEmail).orElse(null);
        }
        if (mongoUser == null && numericUserId > 0) {
            String formattedId = String.format("BOT%05d", numericUserId);
            mongoUser = usersRepository.findById(formattedId).orElse(null);
        }

        if (mongoUser != null && numericUserId <= 0) {
            numericUserId = extractNumericUserId(mongoUser.getId());
        }

        // 2. Check MySQL user (users table)
        UserDetail existingUserDetail = null;
        if (numericUserId > 0) {
            existingUserDetail = findUserDetailById(numericUserId);
        }
        if (existingUserDetail == null && reqEmail != null) {
            existingUserDetail = findUserDetailByEmail(reqEmail);
            if (existingUserDetail != null) {
                numericUserId = existingUserDetail.getUserId();
            }
        }
        if (existingUserDetail == null && mongoUser != null && mongoUser.getEmail() != null && !mongoUser.getEmail().trim().isEmpty()) {
            existingUserDetail = findUserDetailByEmail(mongoUser.getEmail().trim().toLowerCase());
            if (existingUserDetail != null) {
                numericUserId = existingUserDetail.getUserId();
            }
        }

        // 3. Check MySQL login (login table)
        Login existingLogin = null;
        if (numericUserId > 0) {
            existingLogin = findLoginByUserId(numericUserId);
        }
        if (existingLogin == null && reqEmail != null) {
            existingLogin = findLoginByEmail(reqEmail);
            if (existingLogin != null && numericUserId <= 0) {
                numericUserId = existingLogin.getUserId();
            }
        }

        // Re-check MongoDB with MySQL user details if mongoUser was not found initially
        if (mongoUser == null && existingUserDetail != null) {
            mongoUser = usersRepository.findById(String.format("BOT%05d", existingUserDetail.getUserId())).orElse(null);
            if (mongoUser == null && existingUserDetail.getEmail() != null && !existingUserDetail.getEmail().trim().isEmpty()) {
                mongoUser = usersRepository.findByEmail(existingUserDetail.getEmail().trim().toLowerCase()).orElse(null);
            }
        }
        if (mongoUser == null && existingLogin != null) {
            mongoUser = usersRepository.findById(String.format("BOT%05d", existingLogin.getUserId())).orElse(null);
            if (mongoUser == null && existingLogin.getEmail() != null && !existingLogin.getEmail().trim().isEmpty()) {
                mongoUser = usersRepository.findByEmail(existingLogin.getEmail().trim().toLowerCase()).orElse(null);
            }
        }

        // Validation: at least one existing record or sufficient info to create
        if (mongoUser == null && existingUserDetail == null && existingLogin == null) {
            boolean hasEmail = reqEmail != null && !reqEmail.isEmpty();
            boolean hasName = request.getFirstName() != null && !request.getFirstName().trim().isEmpty();
            if (!hasEmail && !hasName) {
                throw new Exception("User not found. Please provide a valid id, userId, or email.");
            }
        }

        // Ensure numericUserId is determined
        if (numericUserId <= 0) {
            numericUserId = dbManager.nextLongPrimaryKey(UserDetail.class);
        }

        Date utilDate = new Date();
        Timestamp timestamp = new Timestamp(utilDate.getTime());
        Instant now = Instant.now();

        // Snapshots of original state for rollback
        Users originalMongoUser = cloneMongoUser(mongoUser);
        UserDetail originalUserDetail = cloneUserDetail(existingUserDetail);
        Login originalLogin = cloneLogin(existingLogin);

        boolean mongoMutated = false;
        boolean userDetailMutated = false;
        boolean loginMutated = false;

        String savedMongoId = (mongoUser != null && mongoUser.getId() != null && !mongoUser.getId().trim().isEmpty())
                ? mongoUser.getId().trim()
                : (reqId != null ? reqId : String.format("BOT%05d", numericUserId));
        Long savedLoginId = (existingLogin != null) ? existingLogin.getLoginId() : null;

        try {
            // 1. MongoDB users collection: if not exists create user record, else update
            if (mongoUser == null) {
                String firstName = request.getFirstName() != null && !request.getFirstName().trim().isEmpty()
                        ? request.getFirstName().trim()
                        : (existingUserDetail != null && existingUserDetail.getFirstName() != null ? existingUserDetail.getFirstName() : "");
                String lastName = request.getLastName() != null
                        ? request.getLastName().trim()
                        : (existingUserDetail != null && existingUserDetail.getLastName() != null ? existingUserDetail.getLastName() : "");
                String email = reqEmail != null
                        ? reqEmail
                        : (existingUserDetail != null && existingUserDetail.getEmail() != null ? existingUserDetail.getEmail().toLowerCase() : "");
                String avatarUrl = request.getAvatarUrl() != null
                        ? request.getAvatarUrl()
                        : (existingUserDetail != null && existingUserDetail.getImageURL() != null ? existingUserDetail.getImageURL() : "");

                String username;
                if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
                    username = request.getUsername().trim();
                } else if (!lastName.isEmpty()) {
                    username = firstName + "_" + lastName;
                } else {
                    username = firstName;
                }

                String status;
                if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
                    status = request.getStatus();
                } else if (request.getIsActive() != null) {
                    status = request.getIsActive() ? "ACTIVE" : "INACTIVE";
                } else if (existingUserDetail != null) {
                    status = existingUserDetail.isActive() ? "ACTIVE" : "INACTIVE";
                } else {
                    status = "ACTIVE";
                }

                mongoUser = Users.builder()
                        .id(savedMongoId)
                        .firstName(firstName)
                        .lastName(lastName)
                        .email(email)
                        .username(username)
                        .avatarUrl(avatarUrl)
                        .status(status)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                mongoUser = usersRepository.save(mongoUser);
                mongoMutated = true;
                savedMongoId = mongoUser.getId();
                log.info("Created Users in MongoDB with id: {}", savedMongoId);
            } else {
                if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
                    mongoUser.setFirstName(request.getFirstName().trim());
                }
                if (request.getLastName() != null) {
                    mongoUser.setLastName(request.getLastName().trim());
                }
                if (reqEmail != null) {
                    mongoUser.setEmail(reqEmail);
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
                mongoUser.setUpdatedAt(now);

                mongoUser = usersRepository.save(mongoUser);
                mongoMutated = true;
                savedMongoId = mongoUser.getId();
                log.info("Updated Users in MongoDB for id: {}", mongoUser.getId());
            }

            // 2. MySQL users table (UserDetail): if exists then update, else create new
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
                if (reqEmail != null) {
                    existingUserDetail.setEmail(reqEmail);
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
                userDetailMutated = true;
                log.info("Updated UserDetail in MySQL for userId: {}", existingUserDetail.getUserId());
            } else {
                String firstName = request.getFirstName() != null && !request.getFirstName().trim().isEmpty()
                        ? request.getFirstName().trim()
                        : (mongoUser != null && mongoUser.getFirstName() != null ? mongoUser.getFirstName() : "");
                String lastName = request.getLastName() != null
                        ? request.getLastName().trim()
                        : (mongoUser != null && mongoUser.getLastName() != null ? mongoUser.getLastName() : "");
                String email = reqEmail != null
                        ? reqEmail
                        : (mongoUser != null && mongoUser.getEmail() != null ? mongoUser.getEmail().toLowerCase() : "");
                String mobile = request.getMobile() != null ? request.getMobile().trim() : "";
                String imageURL = request.getAvatarUrl() != null
                        ? request.getAvatarUrl()
                        : (mongoUser != null && mongoUser.getAvatarUrl() != null ? mongoUser.getAvatarUrl() : "");

                boolean isActive;
                if (request.getIsActive() != null) {
                    isActive = request.getIsActive();
                } else if (request.getStatus() != null) {
                    isActive = "ACTIVE".equalsIgnoreCase(request.getStatus());
                } else if (mongoUser != null && mongoUser.getStatus() != null) {
                    isActive = "ACTIVE".equalsIgnoreCase(mongoUser.getStatus());
                } else {
                    isActive = true;
                }

                UserDetail newUserDetail = UserDetail.builder()
                        .userId(numericUserId)
                        .firstName(firstName)
                        .lastName(lastName)
                        .mobile(mobile)
                        .email(email)
                        .pinCode(request.getPinCode() != null ? request.getPinCode() : 0)
                        .roleId(1)
                        .isActive(isActive)
                        .imageURL(imageURL)
                        .address(request.getAddress() != null ? request.getAddress() : "")
                        .city(request.getCity() != null ? request.getCity() : "")
                        .state(request.getState() != null ? request.getState() : "")
                        .country(request.getCountry() != null ? request.getCountry() : "")
                        .gender(request.getGender() != null ? request.getGender() : "m")
                        .maritalStatus(true)
                        .createdBy(0L)
                        .updatedBy(0L)
                        .createdOn(timestamp)
                        .updatedOn(timestamp)
                        .build();

                dbManager.save(newUserDetail);
                existingUserDetail = newUserDetail;
                userDetailMutated = true;
                log.info("Saved UserDetail to MySQL with userId: {}", numericUserId);
            }

            // 3. MySQL login table (Login): if exists then update, else create new
            if (existingLogin != null) {
                if (reqEmail != null) {
                    existingLogin.setEmail(reqEmail);
                }
                if (request.getMobile() != null) {
                    existingLogin.setMobile(request.getMobile().trim());
                }
                if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
                    existingLogin.setPassword(request.getPassword().trim());
                }
                if (request.getIsActive() != null) {
                    existingLogin.setIsActive(request.getIsActive());
                } else if (request.getStatus() != null) {
                    existingLogin.setIsActive("ACTIVE".equalsIgnoreCase(request.getStatus()));
                } else if (existingUserDetail != null) {
                    existingLogin.setIsActive(existingUserDetail.isActive());
                }
                existingLogin.setUpdatedOn(timestamp);

                dbManager.save(existingLogin);
                loginMutated = true;
                savedLoginId = existingLogin.getLoginId();
                log.info("Updated Login in MySQL for userId: {}", numericUserId);
            } else {
                long nextLoginId = dbManager.nextLongPrimaryKey(LoginDetail.class);
                String password = (request.getPassword() != null && !request.getPassword().trim().isEmpty())
                        ? request.getPassword().trim()
                        : generateRandomPassword(8);

                String email = (existingUserDetail != null && existingUserDetail.getEmail() != null)
                        ? existingUserDetail.getEmail()
                        : (reqEmail != null ? reqEmail : (mongoUser != null ? mongoUser.getEmail() : ""));
                String mobile = (existingUserDetail != null && existingUserDetail.getMobile() != null)
                        ? existingUserDetail.getMobile()
                        : (request.getMobile() != null ? request.getMobile().trim() : "");
                boolean isActive = (existingUserDetail != null) ? existingUserDetail.isActive() : true;

                Login login = Login.builder()
                        .loginId(nextLoginId)
                        .userId(numericUserId)
                        .createdBy(0L)
                        .updatedBy(0L)
                        .code("BOT")
                        .email(email)
                        .mobile(mobile)
                        .password(password)
                        .roleId(0)
                        .isAccountConfig(true)
                        .isActive(isActive)
                        .createdOn(timestamp)
                        .updatedOn(timestamp)
                        .build();

                dbManager.save(login);
                existingLogin = login;
                loginMutated = true;
                savedLoginId = nextLoginId;
                log.info("Saved Login to MySQL with loginId: {} for userId: {}", nextLoginId, numericUserId);
            }

            // 4. Verification of mandatory records in all 3 tables: login (MySQL), user (MySQL), users (MongoDB)
            Users verifiedMongoUser = usersRepository.findById(savedMongoId).orElse(null);
            UserDetail verifiedUserDetail = findUserDetailById(numericUserId);
            Login verifiedLogin = findLoginByUserId(numericUserId);

            if (verifiedMongoUser == null || verifiedUserDetail == null || verifiedLogin == null) {
                log.error("Mandatory table verification failed! users (MongoDB): {}, user (MySQL): {}, login (MySQL): {}",
                        verifiedMongoUser != null, verifiedUserDetail != null, verifiedLogin != null);

                rollbackUserChanges(
                        mongoMutated, originalMongoUser, savedMongoId,
                        userDetailMutated, originalUserDetail, numericUserId,
                        loginMutated, originalLogin, savedLoginId
                );

                List<String> missingTables = new ArrayList<>();
                if (verifiedLogin == null) {
                    missingTables.add("login (MySQL)");
                }
                if (verifiedUserDetail == null) {
                    missingTables.add("user (MySQL)");
                }
                if (verifiedMongoUser == null) {
                    missingTables.add("users (MongoDB)");
                }

                throw new Exception("Transaction rolled back: Mandatory record missing in table(s): " + String.join(", ", missingTables));
            }

            return verifiedMongoUser;
        } catch (Exception e) {
            log.error("Error updating user, undoing all changes: {}", e.getMessage(), e);
            rollbackUserChanges(
                    mongoMutated, originalMongoUser, savedMongoId,
                    userDetailMutated, originalUserDetail, numericUserId,
                    loginMutated, originalLogin, savedLoginId
            );
            throw new Exception("Error updating user (all changes undone): " + e.getMessage());
        }
    }
}
