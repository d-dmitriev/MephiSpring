package home.work.hotel.repositories;

import home.work.hotel.entities.Room;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Transactional
public interface RoomRepository extends ReactiveCrudRepository<Room, Long> {
    // Используйте стандартные методы Spring Data
    Flux<Room> findByHotelIdAndAvailableTrueOrderByTimesBooked(Long hotelId);

    // Или добавьте @Query аннотацию для кастомного запроса
    @Query("SELECT * FROM room WHERE hotel_id = :hotelId AND available = true")
    Flux<Room> findAvailableRoomsByHotelId(Long hotelId);

    // Простые методы без сложных параметров
    Flux<Room> findByAvailableTrue();

    Flux<Room> findByHotelId(Long hotelId);

    @Query("""
            SELECT r.* FROM rooms r 
            WHERE r.available = true 
            AND (:hotelId IS NULL OR r.hotel_id = :hotelId)
            ORDER BY r.times_booked ASC, r.id ASC
            """)
    Flux<Room> findRecommendedRooms(@Param("hotelId") Long hotelId);
}
