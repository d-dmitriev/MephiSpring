package home.work.booking.repositories;

import home.work.booking.entities.Booking;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface BookingRepository extends ReactiveCrudRepository<Booking, Long> {
}