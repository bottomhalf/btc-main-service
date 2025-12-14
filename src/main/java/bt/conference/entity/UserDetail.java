package bt.conference.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fierhub.database.annotations.Column;
import com.fierhub.database.annotations.Id;
import com.fierhub.database.annotations.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class UserDetail {
    @Id
    @Column(name = "userId")
    private long userId;

    @Column(name = "firstName")
    private String firstName;

    @Column(name = "lastName")
    private String lastName;

    @Column(name = "fatherName")
    private String fatherName;

    @Column(name = "motherName")
    private String motherName;

    @Column(name = "email")
    private String email;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "alternateNumber")
    private String alternateNumber;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "pinCode")
    private int pinCode;

    @Column(name = "state")
    private String state;

    @Column(name = "country")
    private String country;

    @Column(name = "roleId")
    private int roleId;

    @Column(name = "isActive")
    private boolean isActive;

    @Column(name = "imageURL")
    private String imageURL;

    @Column(name = "createdBy")
    private long createdBy;

    @Column(name = "updatedBy")
    private long updatedBy;

    @Column(name = "createdOn")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date createdOn;

    @Column(name = "updatedOn")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date updatedOn;

    @Column(name = "dateOfBirth")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date dateOfBirth;

    @Column(name = "gender")
    private char gender;

    @Column(name = "maritalStatus")
    private boolean maritalStatus;

    @Column(name = "religionId")
    private int religionId;

    @Column(name = "nationality")
    private String nationality;
}
