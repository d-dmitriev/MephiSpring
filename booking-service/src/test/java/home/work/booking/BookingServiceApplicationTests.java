package home.work.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import home.work.booking.dto.RoomRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class BookingServiceApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    private MockWebServer mockHotelService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public WebClient hotelServiceWebClientForTests() {
            return WebClient.builder()
                    .baseUrl("http://localhost:" + System.getProperty("mock.hotel.service.port", "9090"))
                    .build();
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        mockHotelService = new MockWebServer();
        mockHotelService.start(9090);
        // Передаём URL мок-сервера в контекст через системное свойство
        System.setProperty("mock.hotel.service.url", "http://localhost:" + mockHotelService.getPort());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockHotelService.shutdown();
        System.clearProperty("mock.hotel.service.url");
    }

    @Test
    void shouldAuthenticateUser() {
        String requestBody = """
                {
                  "username": "user@example.com",
                  "password": "password"
                }
                """;
        webTestClient
                .post()
                .uri("/api/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty();
    }

    @Test
    void shouldReturn401ForInvalidCredentials() {
        String requestBody = """
                {
                  "username": "user@example.com",
                  "password": "wrong"
                }
                """;
        webTestClient
                .post()
                .uri("/api/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldCreateBookingWithAutoSelect() throws Exception {
        // === Мокаем /api/rooms/recommend ===
        RoomRequest mockRoom = new RoomRequest(1L);
        String roomJson = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(mockRoom);
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(roomJson));

        // === Мокаем /api/rooms/1/confirm-availability ===
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("true"));

        // Получаем токен
        String authBody = """
                {
                  "username": "user@example.com",
                  "password": "password"
                }
                """;
        String tokenResponse = webTestClient
                .post().uri("/api/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(authBody)
                .exchange()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

        String accessToken = tokenResponse.split("\"accessToken\":\"")[1].split("\"")[0];

        // Создаём бронирование
        String bookingBody = """
                {
                  "startDate": "2026-01-10",
                  "endDate": "2026-01-12",
                  "autoSelect": true,
                  "requestId": "test-req-1"
                }
                """;

        webTestClient
                .post().uri("/api/bookings")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bookingBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");
    }

    @Test
    void shouldDenyBookingCreationWithoutToken() {
        String bookingBody = """
                {
                  "startDate": "2026-01-10",
                  "endDate": "2026-01-12",
                  "autoSelect": true,
                  "requestId": "test-req-2"
                }
                """;
        webTestClient
                .post()
                .uri("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bookingBody)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldCancelBookingOnHotelServiceFailure() throws Exception {
        // Мокаем /api/rooms/recommend → возвращаем комнату 1
        RoomRequest mockRoom = new RoomRequest(2L);
        String roomJson = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(mockRoom);

        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(roomJson));

        // Мокаем /confirm-availability → (ошибка)
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("false"));

        // Получаем токен
        String token = obtainAccessToken("user@example.com", "password");

        // Создаём бронирование
        String bookingBody = """
                {
                  "roomId": "1",
                  "startDate": "2026-02-01",
                  "endDate": "2026-02-03",
                  "autoSelect": true,
                  "requestId": "1"
                }
                """;

        webTestClient
                .post().uri("/api/bookings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bookingBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }

    @Test
    void shouldHandleIdempotencyWithSameRequestId() throws Exception {
        // Мокаем /recommend → комната 1
        RoomRequest mockRoom = new RoomRequest(1L);
        String roomJson = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(mockRoom);
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(roomJson));
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("true")); // confirm

        String token = obtainAccessToken("user@example.com", "password");
        String bookingBody = """
                {
                  "startDate": "2026-03-01",
                  "endDate": "2026-03-03",
                  "autoSelect": true,
                  "requestId": "idempotent-1"
                }
                """;

        // Первый запрос
        String firstResponse = webTestClient
                .post().uri("/api/bookings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bookingBody)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

        // Второй запрос с тем же requestId
        String secondResponse = webTestClient
                .post().uri("/api/bookings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bookingBody)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

        // Ответы должны быть идентичны
        assertEquals(firstResponse, secondResponse);

        // Hotel Service должен быть вызван только один раз (только при первом запросе)
        assertEquals(2, mockHotelService.getRequestCount()); // recommend + confirm
    }

    @Test
    void shouldFailSecondBookingOnSameDates() throws Exception {
        // Первое бронирование: успешно
        RoomRequest mockRoom = new RoomRequest(1L);
        String roomJson = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(mockRoom);
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(roomJson));
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("true")); // confirm OK

        String token = obtainAccessToken("user@example.com", "password");
        String bookingBody1 = """
                {
                  "roomId": "1",
                  "startDate": "2026-04-01",
                  "endDate": "2026-04-03",
                  "autoSelect": false,
                  "requestId": "first-booking"
                }
                """;

        webTestClient
                .post().uri("/api/bookings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bookingBody1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMED");

        // Второе бронирование на те же даты → Hotel Service вернёт false
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(roomJson)); // recommend
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("false")); // confirm → недоступно!

        // Мокаем /release → должен быть вызван
        mockHotelService.enqueue(new MockResponse()
                .setResponseCode(200));

        String bookingBody2 = """
                {
                  "roomId": "1",
                  "startDate": "2026-04-01",
                  "endDate": "2026-04-03",
                  "autoSelect": false,
                  "requestId": "second-booking"
                }
                """;

        webTestClient
                .post().uri("/api/bookings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bookingBody2)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }

    private String obtainAccessToken(String username, String password) {
        String authBody = String.format("""
                {
                  "username": "%s",
                  "password": "%s"
                }
                """, username, password);

        String tokenResponse = webTestClient
                .post().uri("/api/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(authBody)
                .exchange()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

        return tokenResponse.split("\"accessToken\":\"")[1].split("\"")[0];
    }
}