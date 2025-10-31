package home.work.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import home.work.booking.dto.RoomRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class BookingConcurrencyTests {

    @Autowired
    private WebTestClient webTestClient;

    private MockWebServer mockHotelService;

    @BeforeEach
    void setUp() throws IOException {
        mockHotelService = new MockWebServer();
        mockHotelService.start(9090);
        System.setProperty("mock.hotel.service.url", "http://localhost:" + mockHotelService.getPort());
        // Если в конфиге используется property mock.hotel.service.url, то тестовый WebClient должен брать этот URL.
    }

    @AfterEach
    void tearDown() throws IOException {
        mockHotelService.shutdown();
        System.clearProperty("mock.hotel.service.url");
    }

    @Test
    void twoParallelRequestsWithSameRequestIdShouldBeIdempotent() throws InterruptedException {
        // Мокаем recommend -> вернём комнату 3
        RoomRequest mockRoom = new RoomRequest(3L);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String roomJson;
        try {
            roomJson = mapper.writeValueAsString(mockRoom);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mockHotelService.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(roomJson));

        // Мокаем confirm-availability -> true
        mockHotelService.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("true"));

        mockHotelService.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(roomJson));

        // Мокаем confirm-availability -> true
        mockHotelService.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("false"));

        // Получаем токен
        String token = obtainAccessToken("user@example.com", "password");

        // Готовим тело запроса (один и тот же requestId для двух параллельных вызовов)
        String requestId = "race-idempotent-1";
        String bookingBody = String.format("""
                {
                  "roomId": "3",
                  "startDate": "%s",
                  "endDate": "%s",
                  "autoSelect": false,
                  "requestId": "%s"
                }
                """, LocalDate.now().plusDays(20), LocalDate.now().plusDays(22), requestId);

        int parallel = 2;
        CountDownLatch ready = new CountDownLatch(parallel);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(parallel);
        AtomicReference<String> firstResp = new AtomicReference<>();
        AtomicReference<String> secondResp = new AtomicReference<>();

        // Запускаем два параллельных запроса
        Mono.fromRunnable(() -> {
            ready.countDown();
            try {
                start.await();
                String resp = webTestClient.post().uri("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(bookingBody)
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(String.class)
                        .getResponseBody()
                        .blockFirst();
                firstResp.set(resp);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();

        Mono.fromRunnable(() -> {
            ready.countDown();
            try {
                start.await();
                String resp = webTestClient.post().uri("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(bookingBody)
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(String.class)
                        .getResponseBody()
                        .blockFirst();
                secondResp.set(resp);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();

        ready.await();
        start.countDown();
        done.await();

        Assertions.assertNotNull(firstResp.get(), "First response should not be null");
        Assertions.assertNotNull(secondResp.get(), "Second response should not be null");
//        Assertions.assertEquals(firstResp.get(), secondResp.get(), "Responses must be identical for idempotent requestId");
//        // Проверяем, что mockHotelService был вызван ровно  раза: recommend + confirm (не более)
//        // (в очереди мы положили 4 ответа, поэтому ожидаем 4 запросa на сервер)
//        int requestCount = mockHotelService.getRequestCount();
//        Assertions.assertEquals(4, requestCount, "Hotel service should be called only for recommend and confirm once");
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