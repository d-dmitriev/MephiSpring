package home.work.gateway;

import jakarta.ws.rs.core.HttpHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {
    public JwtAuthenticationGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange,chain) -> {
            String path = exchange.getRequest().getPath().toString();

            // Пропускаем публичные эндпойнты
            if (path.startsWith("/api/auth")) {
                return chain.filter(exchange);
            }
            // Для защищенных эндпойнтов извлекаем токен и передаем его дальше
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                // Check jwt exists
                if (jwt.isEmpty()) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                }
                return chain.filter(exchange);
            }

            // Если токена нет на защищенном эндпойнте, возвращаем 401
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return chain.filter(exchange);
        };
    }

    public static class Config {
        // Filter configuration properties
    }
}
