package home.work.booking.mappers;

import home.work.booking.dto.UserResponse;
import home.work.booking.entities.UserWithRoles;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toDto(UserWithRoles userWithRoles);
}
