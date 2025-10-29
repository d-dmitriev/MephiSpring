package home.work.booking.init;

import home.work.booking.entities.BookingStatus;
import home.work.booking.entities.User;
import home.work.booking.entities.Booking;
import home.work.booking.repositories.UserRepository;
import home.work.booking.repositories.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final DatabaseClient databaseClient;
    private final PasswordEncoder passwordEncoder;

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // Очистка и создание тестовых данных
        databaseClient.sql("DELETE FROM bookings").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM user_roles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM users").fetch().rowsUpdated())
                .thenMany(
                        Flux.just(
                                    User.builder()
                                            .username("user@example.com")
                                            .password(passwordEncoder.encode("password"))
                                            .build(),
                                    User.builder()
                                            .username("admin@example.com")
                                            .password(passwordEncoder.encode("admin"))
                                            .build(),
                                    User.builder()
                                            .username("manager@example.com")
                                            .password(passwordEncoder.encode("manager"))
                                            .build()
                                )
                                .flatMap(user ->
                                        userRepository.save(user)
                                                .flatMap(savedUser ->
                                                        // Сохраняем роли для пользователя
                                                        saveUserRoles(savedUser.getId(), getUserRoles(savedUser.getUsername()))
                                                                .thenReturn(savedUser)
                                                )
                                )
                )
                .flatMap(user -> {
                    // Создаем тестовые бронирования
                    return Flux.just(
                                    Booking.builder()
                                            .userId(user.getId())
                                            .roomId(1L) // ID комнаты из hotel-service
                                            .startDate(LocalDate.now().plusDays(1))
                                            .endDate(LocalDate.now().plusDays(3))
                                            .status(BookingStatus.CONFIRMED)
                                            .createdAt(LocalDate.now())
                                            .build(),
                                    Booking.builder()
                                            .userId(user.getId())
                                            .roomId(2L) // ID комнаты из hotel-service
                                            .startDate(LocalDate.now().plusDays(5))
                                            .endDate(LocalDate.now().plusDays(7))
                                            .status(BookingStatus.PENDING)
                                            .createdAt(LocalDate.now())
                                            .build()
                            )
                            .flatMap(bookingRepository::save);
                })
                .subscribe(
                        booking -> log.info("Initialized booking: {}", booking.getId()),
                        error -> log.info("Error initializing data: {}", String.valueOf(error)),
                        () -> log.info("Booking data initialization completed")
                );
    }

    private List<String> getUserRoles(String username) {
        switch (username) {
            case "user@example.com":
                return List.of("USER");
            case "admin@example.com":
                return List.of("USER", "ADMIN");
            case "manager@example.com":
                return List.of("USER", "MANAGER");
            default:
                return List.of("USER");
        }
    }

    private Mono<Void> saveUserRoles(Long userId, List<String> roles) {
        return Flux.fromIterable(roles)
                .flatMap(role ->
                        databaseClient.sql("INSERT INTO user_roles (user_id, role) VALUES (:userId, :role)")
                                .bind("userId", userId)
                                .bind("role", role)
                                .fetch()
                                .rowsUpdated()
                )
                .then();
    }
}