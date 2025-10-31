package home.work.booking;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
class TestConfig {
    @Bean
    @Primary
    public WebClient hotelServiceWebClientForTests() {
        return WebClient.builder()
                .baseUrl("http://localhost:" + System.getProperty("mock.hotel.service.port", "9090"))
                .build();
    }
}
