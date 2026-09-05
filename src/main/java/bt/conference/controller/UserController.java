package bt.conference.controller;

import bt.conference.serviceinterface.IUserService;
import com.fierhub.model.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/user/")
public class UserController {
    @Autowired
    IUserService _userService;

    @GetMapping("getAllUser")
    public BaseResponse getAllUser() throws Exception {
        var result = _userService.getAllUserService();
        return BaseResponse.Ok(result);
    }

    @PostMapping("register")
    public BaseResponse registerUser(@RequestBody bt.conference.dto.RegisterUserRequest request) throws Exception {
        _userService.registerUserService(request);
        return BaseResponse.Ok("User registered successfully");
    }

    @PutMapping("update")
    public BaseResponse updateUser(@RequestBody bt.conference.dto.UpdateUserRequest request) throws Exception {
        _userService.updateUserService(request);
        return BaseResponse.Ok("User updated successfully");
    }

    @PostMapping("update")
    public BaseResponse updateUserPost(@RequestBody bt.conference.dto.UpdateUserRequest request) throws Exception {
        _userService.updateUserService(request);
        return BaseResponse.Ok("User updated successfully");
    }
}
