package home.work.booking.mappers;

import home.work.booking.dto.BookingResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookingMapper {
    BookingResponse toDto(home.work.booking.entities.Booking model);
}
