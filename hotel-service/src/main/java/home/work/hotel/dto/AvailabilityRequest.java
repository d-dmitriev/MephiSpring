package home.work.hotel.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private String bookingId;

    private String requestId;
    private String userId; // Для аудита
    private String userName; // Для аудита

    // Метод для проверки корректности дат
    public boolean isValid() {
        return startDate != null &&
                endDate != null &&
                !startDate.isAfter(endDate) &&
                !startDate.isBefore(LocalDate.now());
    }

    // Метод для получения количества ночей
    public int getNights() {
        if (startDate == null || endDate == null) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
    }
}
