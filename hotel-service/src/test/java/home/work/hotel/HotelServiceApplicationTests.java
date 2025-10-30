package home.work.hotel;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class HotelServiceApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldGetHotelsAsUser() {
        // Получаем токен USER
        String token = generateJwtToken("user@example.com", List.of("USER"));

        webTestClient
                .get()
                .uri("/api/hotels")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].name").isEqualTo("Hotel 1");
    }

    @Test
    void shouldGetRecommendedRooms() {
        String token = generateJwtToken("user@example.com", List.of("USER"));

        webTestClient
                .get()
                .uri("/api/rooms/recommend?startDate=2026-01-10&endDate=2026-01-12")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray();
    }

    private String generateJwtToken(String username, List<String> roles) {
        String secret = "a-string-secret-at-least-256-bits-long"; // как в application.yml
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        String rolesStr = String.join(",", roles);

        return Jwts.builder()
                .subject(username)
                .claim("roles", rolesStr)
                .issuedAt(new Date())
                .expiration(new Date(Instant.now().plusSeconds(3600).toEpochMilli()))
                .signWith(key)
                .compact();
    }
}
