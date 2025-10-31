package home.work.booking;

import me.yaman.can.webflux.h2console.H2ConsoleAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;

@ComponentScan("home.work")
@SpringBootApplication
@EnableDiscoveryClient
@Import(value={H2ConsoleAutoConfiguration.class})
public class BookingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient hotelServiceWebClient(WebClient.Builder builder) {
        return builder.baseUrl("http://hotel-service").build();
    }
}

