package home.work.booking.repositories;

import home.work.booking.entities.Booking;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Transactional
public interface BookingRepository extends ReactiveCrudRepository<Booking, Long> {
    Flux<Booking> findAllByUserId(Long userId);
}