package bt.conference.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateUserRequest {
    private String id;              // MongoDB user ID, e.g. "BOT00001"
    private Long userId;            // MySQL numeric user ID, e.g. 1
    private String firstName;
    private String lastName;
    private String email;
    private String mobile;
    private String username;
    private String avatarUrl;
    private String status;          // "ACTIVE", "INACTIVE", etc.
    private String password;        // Optional new password
    private String address;
    private String city;
    private String state;
    private String country;
    private Integer pinCode;
    private String gender;
    private Boolean isActive;
}
