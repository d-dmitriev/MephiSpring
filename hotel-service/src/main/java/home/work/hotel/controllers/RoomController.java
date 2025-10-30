package home.work.hotel.controllers;

import home.work.hotel.dto.AvailabilityRequest;
import home.work.hotel.dto.RoomRequest;
import home.work.hotel.dto.RoomResponse;
import home.work.hotel.services.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RoomResponse> addRoom(@RequestBody RoomRequest room) {
        return roomService.addRoom(room);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public Flux<RoomResponse> getAvailableRooms() {
        return roomService.getAvailableRooms();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<RoomResponse> getRooms() {
        return roomService.getRooms();
    }

    @GetMapping("/recommend")
    @PreAuthorize("hasRole('USER')")
    public Flux<RoomResponse> getRecommendedRooms(@RequestParam(required = false) Long hotelId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return roomService.getRecommendedRooms(hotelId, startDate, endDate);
    }

    @PreAuthorize("hasRole('INTERNAL')")
    @PostMapping("/{id}/confirm-availability")
    public Mono<Boolean> confirmAvailability(@PathVariable Long id,
                                             @RequestBody AvailabilityRequest request) {
        return roomService.confirmAvailability(id, request.getStartDate(), request.getEndDate(), request.getBookingId());
    }

    @PreAuthorize("hasRole('INTERNAL')")
    @PostMapping("/{id}/release")
    public Mono<Void> releaseRoom(@PathVariable Long id,
                                  @RequestBody AvailabilityRequest request) {
        return roomService.releaseRoom(id, request.getStartDate(), request.getEndDate());
    }
}
