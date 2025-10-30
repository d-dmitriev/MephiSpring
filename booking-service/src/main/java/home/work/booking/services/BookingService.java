package home.work.booking.services;

import home.work.booking.dto.AvailabilityRequest;
import home.work.booking.dto.BookingResponse;
import home.work.booking.dto.RoomRequest;
import home.work.booking.entities.Booking;
import home.work.booking.entities.BookingStatus;
import home.work.booking.entities.User;
import home.work.booking.exceptions.RoomNotAvailableException;
import home.work.booking.exceptions.UserNotFoundException;
import home.work.booking.repositories.BookingRepository;
import home.work.booking.repositories.ProcessedRequestRepository;
import home.work.booking.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ProcessedRequestRepository processedRequestRepository;
    private final DatabaseClient databaseClient;
    private final WebClient hotelServiceWebClient;
    private final JwtService jwtService;
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    public Mono<BookingResponse> getBooking(Long id) {
        return bookingRepository.findById(id).map(this::convertToDto);
    }

    public Flux<BookingResponse> getBookings() {
        return bookingRepository.findAll().map(this::convertToDto);
    }

    public Flux<BookingResponse> getUserBookings(String userName) {
        return userRepository
                .findByUsername(userName)
                .switchIfEmpty(Mono.error(new UserNotFoundException()))
                .map(User::getId)
                .flatMapMany(userId ->
                        bookingRepository.findAllByUserId(userId)
                                .map(this::convertToDto)
                );
    }

    public Mono<Void> deleteBooking(Long id) {
        return bookingRepository.deleteById(id);
    }

    public Mono<BookingResponse> createBooking(String userName, Long roomId, LocalDate start, LocalDate end, boolean autoSelect, String requestId) {
        // Идемпотентность: проверяем, не обрабатывали ли уже этот requestId
        if (requestId == null || requestId.isBlank()) {
            return Mono.error(new IllegalArgumentException("requestId is required for idempotency"));
        }
        return processedRequestRepository.existsByRequestId(requestId)
                .flatMap(exists -> {
                    if (exists) {
                        // Уже обрабатывали — возвращаем существующее бронирование
                        return processedRequestRepository.findById(requestId)
                                .flatMap(pr -> bookingRepository.findById(pr.getBookingId()))
                                .switchIfEmpty(Mono.error(new RuntimeException("Inconsistent state: processed request without booking")))
                                .map(this::convertToDto);
                    }

                    // Новый запрос — продолжаем создание
                    return proceedWithNewBooking(userName, roomId, start, end, autoSelect, requestId);
                });
    }

    private Mono<BookingResponse> proceedWithNewBooking(String userName, Long roomId, LocalDate start, LocalDate end, boolean autoSelect, String requestId) {
        Mono<Long> userIdMono = userRepository
                .findByUsername(userName)
                .switchIfEmpty(Mono.error(new UserNotFoundException()))
                .map(User::getId);

        if (autoSelect) {
            return userIdMono
                    .flatMap(userId -> hotelServiceWebClient
                            .get()
                            .uri("/api/rooms/recommend?startDate={start}&endDate={end}", start, end)
                            .header("Authorization", "Bearer " + getInternalToken())
                            .retrieve()
                            .bodyToFlux(RoomRequest.class)
                            .next()
                            .switchIfEmpty(Mono.error(new RoomNotAvailableException("No available rooms")))
                            .flatMap(roomDto -> createAndConfirmBooking(userId, roomDto.getId(), start, end, requestId))
                    )
                    .map(this::convertToDto);
        } else {
            return userIdMono
                    .flatMap(userId -> createAndConfirmBooking(userId, roomId, start, end, requestId))
                    .map(this::convertToDto);
        }
    }

    private Mono<Booking> createAndConfirmBooking(Long userId, Long roomId, LocalDate start, LocalDate end, String requestId) {
        Booking pending = Booking.builder()
                .userId(userId)
                .roomId(roomId)
                .startDate(start)
                .endDate(end)
                .status(BookingStatus.PENDING)
                .createdAt(LocalDate.now())
                .build();

        return bookingRepository.save(pending)
                .doOnSuccess(saved -> log.info("Booking saved as PENDING | bookingId={}, requestId={}", saved.getId(), requestId))
                .flatMap(saved -> confirmWithHotel(saved, requestId))
                .flatMap(confirmedBooking -> {
                    // Сохраняем в processed_requests только после успешного подтверждения или отмены
                    return saveProcessedRequest(requestId, confirmedBooking.getId())
                            .thenReturn(confirmedBooking);
                });
    }

    private Mono<Void> saveProcessedRequest(String requestId, Long bookingId) {
        String sql = """
                INSERT INTO processed_requests (request_id, booking_id, processed_at)
                VALUES (:requestId, :bookingId, CURRENT_TIMESTAMP)
                """;
        return databaseClient.sql(sql)
                .bind("requestId", requestId)
                .bind("bookingId", bookingId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private String getInternalToken() {
        return jwtService.generateToken(
                org.springframework.security.core.userdetails.User
                        .builder()
                        .username("system")
                        .password("system")
                        .authorities("INTERNAL,USER")
                        .build());
    }

    private Mono<Booking> confirmWithHotel(Booking booking, String requestId) {
        log.info("Requesting room availability confirmation | bookingId={}, roomId={}, requestId={}",
                booking.getId(), booking.getRoomId(), requestId);

        AvailabilityRequest req = AvailabilityRequest.builder()
                .startDate(booking.getStartDate())
                .endDate(booking.getEndDate())
                .bookingId(booking.getId().toString())
                .requestId(requestId)
                .build();

        return hotelServiceWebClient
                .post()
                .uri("/api/rooms/{id}/confirm-availability", booking.getRoomId())
                .header("Authorization", "Bearer " + getInternalToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(Boolean.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500)))
                .onErrorResume(e -> {
                    log.warn("Failed to confirm availability for booking | bookingId={}, error={}",
                            booking.getId(), e.getMessage());
                    // Компенсация
                    return releaseRoomInHotel(booking.getRoomId(), booking.getStartDate(), booking.getEndDate(), requestId)
                            .doOnSuccess(v -> log.info("Compensation completed: room released | bookingId={}", booking.getId()))
                            .thenReturn(false);
                })
                .flatMap(confirmed -> {
                    BookingStatus newStatus = confirmed ? BookingStatus.CONFIRMED : BookingStatus.CANCELLED;
                    log.info("Booking status updated | bookingId={}, status={}, confirmed={}",
                            booking.getId(), newStatus, confirmed);
                    booking.setStatus(newStatus);
                    return bookingRepository.save(booking);
                });
    }

    private Mono<Void> releaseRoomInHotel(Long roomId, LocalDate start, LocalDate end, String requestId) {
        log.info("Releasing room due to failure | roomId={}, startDate={}, endDate={}, requestId={}",
                roomId, start, end, requestId);

        AvailabilityRequest req = AvailabilityRequest.builder()
                .startDate(start)
                .endDate(end)
                .build();

        return hotelServiceWebClient
                .post()
                .uri("/api/rooms/{id}/release", roomId)
                .header("Authorization", "Bearer " + getInternalToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    private BookingResponse convertToDto(Booking booking) {
        return BookingResponse
                .builder()
                .id(booking.getId())
                .userId(booking.getUserId())
                .roomId(booking.getRoomId())
                .startDate(booking.getStartDate())
                .endDate(booking.getEndDate())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
