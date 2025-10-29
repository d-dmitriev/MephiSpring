package home.work.hotel.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomResponse {
    private Long id;
    private Integer number;
    private Boolean available;
    private Long hotelId;
    private Integer booked;
}
