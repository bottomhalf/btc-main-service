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
public class RegisterUserRequest {
    private String firstName;
    private String lastName;
    private String mobile;
    private String email;
    private String password;
    private String username;
    private String avatarUrl;
}
