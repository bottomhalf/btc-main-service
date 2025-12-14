package bt.conference.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Document(collection = "user_cache")
public class UserCache {

    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("username")
    private String username;

    @Field("email")
    private String email;

    @Field("first_name")
    private String firstName;

    @Field("last_name")
    private String lastName;

    @Field("avatar")
    private String avatar;

    @Field("status")
    private String status;

    @Field("is_active")
    private Boolean isActive;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Field("synced_at")
    private Instant syncedAt;
}