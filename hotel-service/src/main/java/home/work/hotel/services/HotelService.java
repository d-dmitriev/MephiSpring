package home.work.hotel.services;

import home.work.hotel.dto.HotelRequest;
import home.work.hotel.dto.HotelResponse;
import home.work.hotel.entities.Hotel;
import home.work.hotel.repositories.HotelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
public class HotelService {
    private final HotelRepository hotelRepository;

    public Mono<HotelResponse> addHotel(HotelRequest hotel) {
        return hotelRepository.save(Hotel
                        .builder()
                        .name(hotel.getName())
                        .address(hotel.getAddress())
                        .build()
                )
                .map(this::convertToDto);
    }

    public Flux<HotelResponse> getHotels() {
        return hotelRepository.findAll().map(this::convertToDto);
    }

    private HotelResponse convertToDto(Hotel hotel) {
        return HotelResponse
                .builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .address(hotel.getAddress())
                .build();
    }
}
