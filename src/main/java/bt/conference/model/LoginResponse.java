package bt.conference.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    String email;
    String userId;
    String firstName;
    String lastName;
    String token;
    String code;
}
