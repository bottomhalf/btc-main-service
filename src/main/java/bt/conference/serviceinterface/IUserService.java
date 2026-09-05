package bt.conference.serviceinterface;

import bt.conference.dto.RegisterUserRequest;
import bt.conference.dto.UpdateUserRequest;
import bt.conference.entity.UserDetail;

import java.util.List;

public interface IUserService {
    List<UserDetail> getAllUserService() throws Exception;
    void registerUserService(RegisterUserRequest request) throws Exception;
    void updateUserService(UpdateUserRequest request) throws Exception;
}
