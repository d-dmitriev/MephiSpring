package home.work.hotel.mappers;

import home.work.hotel.dto.HotelResponse;
import home.work.hotel.entities.Hotel;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HotelMapper {
    HotelResponse toDto(Hotel hotel);
}
