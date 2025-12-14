package bt.conference.service;

import bt.conference.model.ApplicationConstant;
import bt.conference.entity.LoginDetail;
import bt.conference.model.LoginResponse;
import bt.conference.serviceinterface.IAuthenticateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fierhub.database.utils.DbParameters;
import com.fierhub.database.utils.DbProcedureManager;
import com.fierhub.service.FierhubService;
import in.bottomhalf.common.models.ApiAuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.Arrays;
import java.util.List;

@Service
public class AuthenticateService implements IAuthenticateService {
    @Autowired
    FierhubService fierhubService;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DbProcedureManager dbProcedureManager;
    public LoginResponse authenticateUserService(LoginDetail loginDetail) throws Exception {
        if (loginDetail.getEmail() == null || loginDetail.getEmail().isEmpty())
            throw new Exception("Please enter email");

        if (loginDetail.getPassword() == null || loginDetail.getPassword().isEmpty())
            throw new Exception("Please enter password");

        LoginDetail userDetail = getUserByEmailOrMobile(loginDetail.getEmail(), null);

        ApiAuthResponse response = fierhubService.generateToken(userDetail, String.valueOf(userDetail.getUserId()), List.of(ApplicationConstant.Admin));

        return LoginResponse.builder()
                .token(response.getAccessToken())
                .firstName(userDetail.getFirstName())
                .lastName(userDetail.getLastName())
                .email(userDetail.getEmail())
                .userId(userDetail.getUserId())
                .code(userDetail.getCode())
                .build();
    }

    private LoginDetail getUserByEmailOrMobile(String email, String mobile) throws Exception {
        var loginDetail = dbProcedureManager.execute("sp_login_auth",
                Arrays.asList(
                        new DbParameters("_mobile", mobile, Types.VARCHAR),
                        new DbParameters("_email", email, Types.VARCHAR)
                ), LoginDetail.class
        );
        if (loginDetail == null)
            throw new Exception("Fail to get user detail. Please contact to admin.");

        return loginDetail;
    }
}
