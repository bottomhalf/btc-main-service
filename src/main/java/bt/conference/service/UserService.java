package bt.conference.service;

import bt.conference.dto.RegisterUserRequest;
import bt.conference.dto.UpdateUserRequest;
import bt.conference.entity.Login;
import bt.conference.entity.LoginDetail;
import bt.conference.entity.UserDetail;
import bt.conference.entity.Users;
import bt.conference.repository.UsersRepository;
import bt.conference.serviceinterface.IUserService;
import com.fierhub.database.service.DbManager;
import com.fierhub.database.utils.ProcedureManager;
import com.fierhub.model.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class UserService implements IUserService {
    @Autowired
    DbManager dbManager;
    @Autowired
    UsersRepository usersRepository;
    @Autowired
    UsersService usersService;
    @Autowired
    UserSession userSession;

    public List<UserDetail> getAllUserService() throws Exception {
        return dbManager.get(UserDetail.class);
    }


    public void registerUserService(RegisterUserRequest request) throws Exception {
        // 1. Create and save UserDetail (MySQL)
        Date utilDate = new Date();
        var date = new Timestamp(utilDate.getTime());
        var nextUserId = dbManager.nextLongPrimaryKey(UserDetail.class);
        UserDetail userDetail = UserDetail.builder()
                .userId(nextUserId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .pinCode(0)
                .roleId(1)
                .isActive(true)
                .createdBy(0L)
                .updatedBy(0L)
                .createdOn(date)
                .updatedOn(date)
                .gender("m")
                .maritalStatus(true)
                .build();
        
        dbManager.save(userDetail); 

        // 2. Create and save LoginDetail (MySQL)
        Login loginDetail = Login.builder()
                .loginId(dbManager.nextLongPrimaryKey(LoginDetail.class))
                .userId(nextUserId)
                .createdBy(0L)
                .updatedBy(0L)
                .code("BOT")
                .email(request.getEmail())
                .password(generateRandomPassword(8))
                .roleId(0)
                .isAccountConfig(true)
                .isActive(true)
                .createdOn(date)
                .updatedOn(date)
                .build();
        String code = "BOT";

        dbManager.save(loginDetail);

        // 3. Create and save Users (MongoDB)
        String formattedUserId = String.format("%s%05d", code, nextUserId);
        Instant now = Instant.now();

        Users mongoUser = Users.builder()
                .id(formattedUserId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .createdAt(now)
                .updatedAt(now)
                .status("ACTIVE")
                .avatarUrl("")
                .username(request.getFirstName() + "_" + request.getLastName())
                .build();

        usersRepository.save(mongoUser);
    }

    public String deleteUserService(Long userId) throws Exception {
        if (userId == null || userId <= 0)
            throw new Exception("User id is required");

        var result = dbManager.queryRaw("select * from login where userId = " + userId, Login.class);
        if (result == null)
            throw new Exception("User not found");

        Date utilDate = new Date();
        var date = new Timestamp(utilDate.getTime());
        result.setIsActive(false);
        result.setUpdatedBy(Long.parseLong(userSession.getUserId()));
        result.setUpdatedOn(date);
        dbManager.save(result);

        return "User deleted successfully";
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public void updateUserService(UpdateUserRequest request) throws Exception {
        usersService.updateUser(request);
    }
}
