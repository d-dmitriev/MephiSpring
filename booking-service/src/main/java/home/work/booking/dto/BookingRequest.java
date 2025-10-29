package home.work.booking.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BookingRequest {
    private Long roomId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean autoSelect;
    private String requestId;

    public Boolean isAutoSelect() {
        return autoSelect;
    }
}
