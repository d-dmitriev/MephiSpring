package home.work.hotel.controllers;

import home.work.hotel.dto.*;
import home.work.hotel.services.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<HotelResponse> addHotel(@RequestBody HotelRequest hotel) {
        return hotelService.addHotel(hotel);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public Flux<HotelResponse> getHotels() {
        return hotelService.getHotels();
    }
}
