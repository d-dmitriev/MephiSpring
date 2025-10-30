package home.work.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class BookingServiceApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

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
    void shouldCreateBookingWithAutoSelect() {
        // Сначала получаем токен
        String authBody = """
                {
                  "username": "user@example.com",
                  "password": "password"
                }
                """;

        String token = webTestClient
                .post()
                .uri("/api/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(authBody)
                .exchange()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

        // Извлекаем токен из JSON (упрощённо — можно использовать JsonPath, но для краткости — подстрока)
        String accessToken = token.split("\"accessToken\":\"")[1].split("\"")[0];

        String bookingBody = """
                {
                  "startDate": "2026-01-10",
                  "endDate": "2026-01-12",
                  "autoSelect": true,
                  "requestId": "test-req-1"
                }
                """;

        webTestClient
                .post()
                .uri("/api/bookings")
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
