package home.work.hotel.exceptions;

import home.work.exceptions.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestControllerAdvice
public class HotelExceptionHandler {

    @ExceptionHandler(RoomAlreadyExists.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRoomAlreadyExists(RoomAlreadyExists ex, ServerWebExchange exchange) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Room Already Exists",
                ex.getMessage(),
                exchange.getRequest().getPath().value(),
                LocalDateTime.now()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(error));
    }
}
