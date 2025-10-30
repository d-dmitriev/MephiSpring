package home.work.hotel.services;

import home.work.hotel.dto.HotelRequest;
import home.work.hotel.dto.HotelResponse;
import home.work.hotel.entities.Hotel;
import home.work.hotel.mappers.HotelMapper;
import home.work.hotel.repositories.HotelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
public class HotelService {
    private final HotelRepository hotelRepository;
    private final HotelMapper mapper;

    public Mono<HotelResponse> addHotel(HotelRequest hotel) {
        return hotelRepository.save(Hotel
                        .builder()
                        .name(hotel.getName())
                        .address(hotel.getAddress())
                        .build()
                )
                .map(mapper::toDto);
    }

    public Flux<HotelResponse> getHotels() {
        return hotelRepository.findAll().map(mapper::toDto);
    }
}
