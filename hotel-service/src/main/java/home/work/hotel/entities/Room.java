package home.work.hotel.entities;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table("rooms")
public class Room {
    @Id
    private Long id;
    private Long hotelId;
    private Integer number;
    private Boolean available;
    private Integer timesBooked;
}
