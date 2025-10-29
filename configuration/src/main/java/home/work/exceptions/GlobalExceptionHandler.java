package home.work.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBadCredentials(BadCredentialsException ex, ServerWebExchange exchange) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Authentication Failed",
                "Invalid username or password",
                exchange.getRequest().getPath().value(),
                LocalDateTime.now()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDenied(AccessDeniedException ex, ServerWebExchange exchange) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Access Denied",
                "You do not have permission to access this resource",
                exchange.getRequest().getPath().value(),
                LocalDateTime.now()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(error));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        ErrorResponse error = new ErrorResponse(
                ex.getStatusCode().value(),
                ex.getStatusCode().toString(),
                ex.getReason() != null ? ex.getReason() : "Request failed",
                exchange.getRequest().getPath().value(),
                LocalDateTime.now()
        );
        return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(error));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneric(Exception ex, ServerWebExchange exchange) {
        // Не логируем пароли и т.п.
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred",
                exchange.getRequest().getPath().value(),
                LocalDateTime.now()
        );
        // Логирование в продакшене обязательно
        ex.printStackTrace(); // или используйте логгер
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }
}
