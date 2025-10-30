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
}