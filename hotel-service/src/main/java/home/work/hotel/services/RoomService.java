package home.work.hotel.services;

import home.work.hotel.dto.RoomRequest;
import home.work.hotel.dto.RoomResponse;
import home.work.hotel.entities.Room;
import home.work.hotel.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;
    private final DatabaseClient databaseClient;
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    public Mono<RoomResponse> addRoom(RoomRequest room) {
        return roomRepository.save(Room
                        .builder()
                        .hotelId(room.getHotelId())
                        .number(room.getNumber())
                        .build()
                )
                .map(this::convertToDto);
    }

    public Flux<RoomResponse> getAvailableRooms() {
        return roomRepository.findByAvailableTrue()
                .map(this::convertToDto);
    }

    public Flux<RoomResponse> getRooms() {
        return roomRepository.findAll()
                .map(this::convertToDto);
    }

    public Flux<RoomResponse> getRecommendedRooms(Long hotelId, LocalDate startDate, LocalDate endDate) {
//        return roomRepository.findByHotelIdAndAvailableTrueOrderByTimesBooked(hotelId)
//                .map(this::convertToDto);
            return roomRepository.findRecommendedRooms(hotelId)
                    .map(this::convertToDto);
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

    private RoomResponse convertToDto(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .hotelId(room.getHotelId())
                .available(room.getAvailable())
                .number(room.getNumber())
                .booked(room.getTimesBooked())
                .build();
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
                    log.info("Blocking date: {}", date);
                    return databaseClient.sql(
                                "INSERT INTO room_blocked_dates (room_id, blocked_date) VALUES (:roomId, :date)")
                        .bind("roomId", roomId)
                        .bind("date", date)
                        .fetch()
                        .rowsUpdated();});
        return inserts.then();
    }
}
