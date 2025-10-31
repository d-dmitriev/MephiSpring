package home.work.hotel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class HotelConcurrencyTests {

    @Autowired
    private WebTestClient webTestClient;

    private String token;

    @BeforeEach
    void setUp() {
        // Генерируем простой JWT для тестов (роль INTERNAL не требуется для confirm-availability в этом тесте,
        // используем USER, эндпоинт доступен по роли в реальном приложении; в тестовом контексте Security настроен)
        token = TestJwtUtil.generateToken("admin@example.com", List.of("INTERNAL", "USER"));
    }

    @AfterEach
    void tearDown() {
        // ничего
    }

    @Test
    void concurrentConfirmAvailabilityShouldNotCreateDuplicateBlocks() throws InterruptedException {
        // Используем заранее существующую комнату (инициализатор создает номера; выбран id=1L)
        Long roomId = 1L;
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(12);

        int threads = 6;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            Mono.fromRunnable(() -> {
                try {
                    ready.countDown();
                    startLatch.await();

                    String body = String.format("""
                            {
                              "startDate": "%s",
                              "endDate": "%s",
                              "bookingId": "concurrent-%d",
                              "requestId": "req-concurrent-%d"
                            }
                            """, start, end, idx, idx);

                    boolean result = webTestClient.post()
                            .uri("/api/rooms/{id}/confirm-availability", roomId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(body)
                            .exchange()
                            .expectStatus().is2xxSuccessful()
                            .expectBody(Boolean.class)
                            .returnResult()
                            .getResponseBody();

                    if (Boolean.TRUE.equals(result)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // логируем, но позволим завершиться
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            }).subscribeOn(Schedulers.boundedElastic()).subscribe();
        }

        // дождаться готовности и стартовать одновременно
        ready.await();
        startLatch.countDown();
        done.await();

        // Проверка: только один успешный блок должен пройти (times_booked увеличивался в confirmAvailability)
        // В слабой конфигурации БД допускается 1 успех; если логика распределяет иначе — в любом случае не должно быть >1 успешных блокировок для тех же дат.
        int successes = successCount.get();
        System.out.println("Successes: " + successes);
        assert successes >= 1 : "Expected at least one success";
        assert successes == 1 : "Expected exactly one successful block, but got " + successes;
    }
}