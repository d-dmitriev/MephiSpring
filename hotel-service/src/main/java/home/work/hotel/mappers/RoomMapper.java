package home.work.hotel.mappers;

import home.work.hotel.dto.RoomResponse;
import home.work.hotel.entities.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoomMapper {
    @Mapping(target = "booked", source = "room.timesBooked")
    RoomResponse toDto(Room room);
}
