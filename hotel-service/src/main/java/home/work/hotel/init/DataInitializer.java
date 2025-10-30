package home.work.hotel.init;

import home.work.hotel.entities.Hotel;
import home.work.hotel.entities.Room;
import home.work.hotel.repositories.HotelRepository;
import home.work.hotel.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final DatabaseClient databaseClient;

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // Очистка и создание тестовых данных
        databaseClient.sql("DELETE FROM room_blocked_dates").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM rooms").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM hotels").fetch().rowsUpdated())
                .thenMany(
                        Flux.just(
                                Hotel.builder().name("Hotel 1").address("Address 1").build(),
                                Hotel.builder().name("Hotel 2").address("Address 2").build()
                        ).flatMap(hotelRepository::save)
                )
                .flatMap(hotel -> {
                    Long hotelId = hotel.getId();
                    return Flux.just(
                                    Room.builder().hotelId(hotelId).available(true).number(101).timesBooked(3).build(),
                                    Room.builder().hotelId(hotelId).available(true).number(102).timesBooked(2).build(),
                                    Room.builder().hotelId(hotelId).available(false).number(103).timesBooked(1).build(),
                                    Room.builder().hotelId(hotelId).available(true).number(201).build(),
                                    Room.builder().hotelId(hotelId).available(true).number(202).build()
                            )
                            .flatMap(roomRepository::save);
                })
                .subscribe(
                        room -> log.info("Initialized room: {} for hotel: {}", room.getId(), room.getHotelId()),
                        error -> log.info("Error initializing data: {}", String.valueOf(error)),
                        () -> log.info("Data initialization completed")
                );
    }
}
