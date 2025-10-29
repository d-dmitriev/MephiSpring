package home.work.booking.exceptions;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserNotFoundException extends RuntimeException {
    private final Long userId;

    public String getMessage() {
        return "User not found with id: " + userId;
    }
}
