package home.work.booking.entities;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

@Data
@RequiredArgsConstructor
@Table("user_roles")
public class UserRole {
    private final Long userId;
    private final String role;
}