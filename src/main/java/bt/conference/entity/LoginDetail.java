package bt.conference.entity;

import com.fierhub.database.annotations.Column;
import com.fierhub.database.annotations.Id;
import com.fierhub.database.annotations.Table;
import com.fierhub.database.annotations.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "login")
public class LoginDetail {
    @Id
    @Column(name = "loginId")
    String loginId;

    @Column(name = "email")
    String email;

    @Column(name = "password")
    String password;

    @Column(name = "userId")
    String userId;

    @Column(name= "firstName")
    String firstName;

    @Column(name= "lastName")
    String lastName;

    @Column(name= "code")
    String code;
}
