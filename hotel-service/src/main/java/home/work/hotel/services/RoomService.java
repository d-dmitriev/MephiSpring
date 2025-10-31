package home.work.hotel.services;

import home.work.hotel.dto.RoomRequest;
import home.work.hotel.dto.RoomResponse;
import home.work.hotel.entities.Room;
import home.work.hotel.exceptions.RoomAlreadyBookedException;
import home.work.hotel.exceptions.RoomAlreadyExists;
import home.work.hotel.mappers.RoomMapper;
import home.work.hotel.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final RoomMapper mapper;
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    public Mono<RoomResponse> addRoom(RoomRequest room) {
        return roomRepository.save(Room
                        .builder()
                        .hotelId(room.getHotelId())
                        .available(true)
                        .number(room.getNumber())
                        .timesBooked(0)
                        .build()
                )
                .onErrorResume(throwable -> {
                    // Проверяем, не ошибка ли уникальности
                    if (isUniqueConstraintViolation(throwable)) {
                        log.warn("Room {} already exists  in hotel {}", room.getNumber(), room.getHotelId());
                        return Mono.error(new RoomAlreadyExists("Room " +  room.getNumber() + " already exists in hotel " + room.getHotelId()));
                    }
                    return Mono.error(throwable);
                })
                .map(mapper::toDto);
    }

    public Flux<RoomResponse> getAvailableRooms() {
        return roomRepository.findByAvailableTrue()
                .map(mapper::toDto);
    }

    public Flux<RoomResponse> getRooms() {
        return roomRepository.findAll()
                .map(mapper::toDto);
    }

    public Flux<RoomResponse> getRecommendedRooms(Long hotelId, LocalDate startDate, LocalDate endDate) {
//        return roomRepository.findByHotelIdAndAvailableTrueOrderByTimesBooked(hotelId)
//        return roomRepository.findRecommendedRooms(hotelId)
        return roomRepository.findAvailableAndRecommendedRooms(hotelId, startDate, endDate)
                .map(mapper::toDto);
    }

    public Mono<Boolean> confirmAvailability(Long roomId, LocalDate startDate, LocalDate endDate, String bookingId) {
        log.info("Confirming availability | roomId={}, startDate={}, endDate={}, bookingId={}",
                roomId, startDate, endDate, bookingId);

        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return Mono.just(false);
        }

        return roomRepository.findById(roomId)
                .switchIfEmpty(Mono.just(Room.builder().available(false).build()))
                .filter(Room::getAvailable)
                .flatMap(room -> {
                    // Проверяем, не заняты ли даты
                    return isRoomAvailableOnDates(roomId, startDate, endDate)
                            .flatMap(available -> {
                                if (!available) {
                                    log.warn("Room already booked | roomId={}, bookingId={}", roomId, bookingId);
                                    return Mono.just(false);
                                }
                                return blockDates(roomId, startDate, endDate, bookingId)
                                        .doOnSuccess(v -> log.info("Room blocked successfully | roomId={}, bookingId={}", roomId, bookingId))
                                        .then(roomRepository.findById(roomId))
                                        .flatMap(existRoom -> {
                                            existRoom.setTimesBooked(existRoom.getTimesBooked() + 1);
                                            return roomRepository.save(existRoom);
                                        })
                                        .doOnSuccess(updated -> log.info("times_booked incremented | roomId={}, newCount={}", roomId, updated.getTimesBooked()))
                                        .then(Mono.just(true));
                            });
                })
                .onErrorResume(RoomAlreadyBookedException.class, e -> {
                    log.warn("Concurrent booking conflict: {}", e.getMessage());
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }

    public Mono<Void> releaseRoom(Long roomId, LocalDate startDate, LocalDate endDate) {
        String sql = """
                DELETE FROM room_blocked_dates 
                WHERE room_id = :roomId 
                AND blocked_date BETWEEN :start AND :end
                """;
        return databaseClient.sql(sql)
                .bind("roomId", roomId)
                .bind("start", startDate)
                .bind("end", endDate)
                .fetch()
                .rowsUpdated()
                .doOnSuccess(rows -> log.info("Rows deleted: {}", rows))
                .then();
    }

    private Mono<Boolean> isRoomAvailableOnDates(Long roomId, LocalDate start, LocalDate end) {
        String sql = """
                SELECT COUNT(*) as count FROM room_blocked_dates 
                WHERE room_id = :roomId 
                AND blocked_date BETWEEN :start AND :end
                """;
        return databaseClient.sql(sql)
                .bind("roomId", roomId)
                .bind("start", start)
                .bind("end", end)
                .fetch()
                .one()
                .map(row -> {
                    log.info("Rows blocked {}", row);
                    return ((Number) row.get("count")).longValue() == 0;
                });
    }

    private Mono<Void> blockDates(Long roomId, LocalDate start, LocalDate end, String bookingId) {
        List<LocalDate> dates = start.datesUntil(end.plusDays(1)).toList();
        Flux<?> inserts = Flux.fromIterable(dates)
                .flatMap(date -> {
                    log.info("Blocking date: {} for room: {}, booking: {}", date, roomId, bookingId);
                    return databaseClient.sql(
                                    "INSERT INTO room_blocked_dates (room_id, blocked_date) VALUES (:roomId, :date)")
                            .bind("roomId", roomId)
                            .bind("date", date)
                            .fetch()
                            .rowsUpdated()
                            .onErrorResume(throwable -> {
                                // Проверяем, не ошибка ли уникальности
                                if (isUniqueConstraintViolation(throwable)) {
                                    log.warn("Date {} already blocked for room {}, booking {}", date, roomId, bookingId);
                                    return Mono.error(new RoomAlreadyBookedException("Date " + date + " already booked"));
                                }
                                return Mono.error(throwable);
                            });
                });
        return inserts.as(transactionalOperator::transactional).then();
    }

    private boolean isUniqueConstraintViolation(Throwable ex) {
        String msg = ex.getMessage();
        if (msg == null) return false;
        // H2: "Unique index or primary key violation"
        return msg.toLowerCase().contains("unique") || msg.toLowerCase().contains("violation");
    }
}
