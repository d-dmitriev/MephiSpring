package home.work.booking.entities;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserWithRoles {
    private Long id;
    private String username;
    private String password;
    private String roles;
}
