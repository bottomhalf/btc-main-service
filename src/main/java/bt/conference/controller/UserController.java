package bt.conference.controller;

import bt.conference.serviceinterface.IUserService;
import in.bottomhalf.common.models.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/user/")
public class UserController {
    @Autowired
    IUserService _userService;

    @GetMapping("getAllUser")
    public ResponseEntity<ApiResponse> getAllUser() throws Exception {
        var result = _userService.getAllUserService();
        return ResponseEntity.ok(ApiResponse.Ok(result));
    }
}
