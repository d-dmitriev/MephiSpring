package home.work.booking.controllers;

import home.work.booking.dto.BookingRequest;
import home.work.booking.dto.BookingResponse;
import home.work.booking.services.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<BookingResponse> getBookings() {
        return bookingService.getBookings();
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public Mono<BookingResponse> createBooking(@AuthenticationPrincipal Jwt jwt,
                                       @RequestBody BookingRequest request) {
        return bookingService.createBooking(
                jwt.getSubject(), request.getRoomId(), request.getStartDate(),
                request.getEndDate(), request.isAutoSelect(), request.getRequestId()
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public Mono<BookingResponse> getBooking(@PathVariable Long id) {
        return bookingService.getBooking(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteBooking(@PathVariable Long id) {
        return bookingService.deleteBooking(id);
    }
}
