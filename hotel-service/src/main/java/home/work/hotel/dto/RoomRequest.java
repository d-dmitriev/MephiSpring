package home.work.hotel.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomRequest {
    private Long hotelId;
    private Integer number;
}
