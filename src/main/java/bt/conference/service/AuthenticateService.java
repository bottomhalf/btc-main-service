package bt.conference.service;

import bt.conference.entity.LoginRefreshToken;
import bt.conference.model.ApplicationConstant;
import bt.conference.entity.LoginDetail;
import bt.conference.model.LoginResponse;
import bt.conference.serviceinterface.IAuthenticateService;
import com.fierhub.database.service.DbManager;
import com.fierhub.database.utils.DbParameters;
import com.fierhub.database.utils.ProcedureManager;
import com.fierhub.model.ApiAuthResponse;
import com.fierhub.service.FierhubService;
import com.fierhub.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Types;
import java.util.Arrays;
import java.util.List;

@Service
public class AuthenticateService implements IAuthenticateService {
    @Autowired
    FierhubService fierhubService;
    @Autowired
    ProcedureManager dbProcedureManager;
    @Autowired
    JwtService jwtService;


    public ApiAuthResponse authenticateUserService(LoginDetail loginDetail) throws Exception {
        if (loginDetail.getEmail() == null || loginDetail.getEmail().isEmpty())
            throw new Exception("Please enter email");

        if (loginDetail.getPassword() == null || loginDetail.getPassword().isEmpty())
            throw new Exception("Please enter password");

        LoginDetail userDetail = getUserByEmailOrMobile(loginDetail.getEmail(), null);
        if (!userDetail.getPassword().equals(loginDetail.getPassword()))
            throw new Exception("User id address or password is not matching");

        if (!userDetail.getIsActive())
            throw new Exception("User account is not active. Please contact to admin.");

        return getApiAuthResponse(userDetail, "web");
    }

    public ApiAuthResponse authenticateMobileUserService(LoginDetail loginDetail) throws Exception {
        if (loginDetail.getEmail() == null || loginDetail.getEmail().isEmpty())
            throw new Exception("Please enter email");

        if (loginDetail.getPassword() == null || loginDetail.getPassword().isEmpty())
            throw new Exception("Please enter password");

        LoginDetail userDetail = getUserByEmailOrMobile(loginDetail.getEmail(), null);
        if (!userDetail.getPassword().equals(loginDetail.getPassword()))
            throw new Exception("User id address or password is not matching");

        if (!userDetail.getIsActive())
            throw new Exception("User account is not active. Please contact to admin.");

        return getApiAuthResponse(userDetail, "mobile");
    }

    @NotNull
    private ApiAuthResponse getApiAuthResponse(LoginDetail userDetail, String device) throws Exception {
        ApiAuthResponse response = fierhubService.generateToken(
                userDetail,
                userDetail.getUserId(),
                List.of(ApplicationConstant.Admin),
                "btc-conference"
        );

        LoginResponse loginResponse = LoginResponse.builder()
                .token(response.getAccessToken())
                .firstName(userDetail.getFirstName())
                .lastName(userDetail.getLastName())
                .email(userDetail.getEmail())
                .userId(userDetail.getUserId())
                .code(userDetail.getCode())
                .build();

        dbProcedureManager.execute("sp_login_refresh_token_upsert",
                DbParameters.of("_correlation_id", response.getRefreshTokenCorelationId(), Types.VARCHAR),
                DbParameters.of("_email_id", userDetail.getEmail(), Types.VARCHAR),
                DbParameters.of("_device", device, Types.VARCHAR),
                DbParameters.of("_refresh_token", response.getRefreshToken(), Types.VARCHAR)
        );

        // Return the full ApiAuthResponse with RefreshToken intact
        // The AuthCookieFilter will extract RefreshToken, set it as HttpOnly cookie,
        // and strip it from the response body before sending to the client
        var authResponse = ApiAuthResponse.Ok(
                loginResponse,
                response.getAccessToken(),
                response.getRefreshToken(),
                response.getRefreshTokenCorelationId()
        );

        if (device.equals("mobile")) {
            authResponse.setRefreshToken(response.getRefreshToken());
        }

        return authResponse;
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

    public ApiAuthResponse regenerateTokenService(HttpServletRequest request) throws Exception {
        String refreshToken = (String) request.getAttribute("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new Exception("Refresh token not found. Please login again.");
        }

        // Validate the refresh token is not tampered and not expired
        Claims claims;

        try {
            claims = jwtService.extractAllClaims(refreshToken);
        } catch (SignatureException ex) {
            throw new Exception("Invalid refresh token. Token has been tampered. Please login again.");
        } catch (ExpiredJwtException ex) {
            throw new Exception("Refresh token has expired. Please login again.");
        } catch (Exception ex) {
            throw new Exception("Invalid refresh token. Please login again.");
        }

        // Extract data from claims
        String sid = (String) claims.get("sid");           // user id stored as subject

        try {
            var response = dbProcedureManager.get(
                    "sp_login_refresh_token_get",
                    LoginRefreshToken.class,
                    DbParameters.of("_correlation_id", sid, Types.VARCHAR)
            );

            if (response != null && response.getRefresh_token().equals(refreshToken)) {
                System.out.println("Token is valid");
                LoginDetail userDetail = getUserByEmailOrMobile(response.getEmail_id(), null);
                ApiAuthResponse apiAuthResponse = getApiAuthResponse(userDetail, "web");

                if (apiAuthResponse.getAccessToken() == null || apiAuthResponse.getAccessToken().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid request to generate token");
                }

                return apiAuthResponse;
            }
        } catch (Exception ex) {
            throw new Exception("Invalid refresh token. Please login again.");
        }

        // TODO: use extracted sid/claims to regenerate a new access token
        // For now, return a dummy response
        return (ApiAuthResponse) ApiAuthResponse.RaiseError("Fail", new Exception("Refresh token generation failed"));
    }

    public ApiAuthResponse generateAccessTokenService(HttpServletRequest request) throws Exception {
        String refreshToken = request.getHeader("Authorization");

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new Exception("Refresh token not found. Please login again.");
        }

        refreshToken = refreshToken.replace("Bearer", "").trim();

        // Validate the refresh token is not tampered and not expired
        Claims claims;

        try {
            claims = jwtService.extractAllClaims(refreshToken);
        } catch (SignatureException ex) {
            throw new Exception("Invalid refresh token. Token has been tampered. Please login again.");
        } catch (ExpiredJwtException ex) {
            throw new Exception("Refresh token has expired. Please login again.");
        } catch (Exception ex) {
            throw new Exception("Invalid refresh token. Please login again.");
        }

        // Extract data from claims
        String sid = (String) claims.get("sid");           // user id stored as subject

        try {
            var response = dbProcedureManager.get(
                    "sp_login_refresh_token_get",
                    LoginRefreshToken.class,
                    DbParameters.of("_correlation_id", sid, Types.VARCHAR)
            );

            if (response != null && response.getRefresh_token().equals(refreshToken)) {
                System.out.println("Token is valid");
                LoginDetail userDetail = getUserByEmailOrMobile(response.getEmail_id(), null);
                ApiAuthResponse apiAuthResponse = getApiAuthResponse(userDetail, "mobile");

                if (apiAuthResponse.getAccessToken() == null || apiAuthResponse.getAccessToken().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid request to generate token");
                }

                return apiAuthResponse;
            }
        } catch (Exception ex) {
            throw new Exception("Invalid refresh token. Please login again.");
        }

        // TODO: use extracted sid/claims to regenerate a new access token
        // For now, return a dummy response
        return (ApiAuthResponse) ApiAuthResponse.RaiseError("Fail", new Exception("Refresh token generation failed"));
    }
}
