package home.work.hotel.entities;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table("hotels")
public class Hotel {
    @Id
    private Long id;
    private String name;
    private String address;
}
