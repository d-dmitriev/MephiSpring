package home.work.booking.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserRequest {
    private Long id;
    private String username;
    private String password;
    private List<String> roles;
}
