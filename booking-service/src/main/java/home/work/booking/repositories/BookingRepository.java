package home.work.booking.repositories;

import home.work.booking.entities.Booking;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Transactional
public interface BookingRepository extends ReactiveCrudRepository<Booking, Long> {
    Flux<Booking> findAllByUserId(Long userId);

    @Query("""
            SELECT * FROM bookings
            WHERE user_id = :userId
            ORDER BY id DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<Booking> findAllByUserIdWithPagination(
            @Param("userId") Long userId,
            @Param("limit") int limit,
            @Param("offset") int offset);
}