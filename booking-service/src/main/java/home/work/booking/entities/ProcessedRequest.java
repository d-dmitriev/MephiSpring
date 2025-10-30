package home.work.booking.entities;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@Table("processed_requests")
public class ProcessedRequest {
    @Id
    private String requestId;
    private Long bookingId;
    private LocalDateTime processedAt;
}