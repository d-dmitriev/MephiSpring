package home.work.booking.exceptions;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserNotFoundException extends RuntimeException {
    private Long userId = 0L;

    public String getMessage() {
        return userId == 0L ? "User not found" :  "User not found with id: " + userId;
    }
}
