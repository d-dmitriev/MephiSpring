package home.work.hotel.repositories;

import home.work.hotel.entities.Hotel;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Transactional
public interface HotelRepository extends ReactiveCrudRepository<Hotel, Long> {
    Mono<Hotel> findByName(String name);
}
