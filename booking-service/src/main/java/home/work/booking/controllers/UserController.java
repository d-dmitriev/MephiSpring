package home.work.booking.controllers;

import home.work.booking.dto.UserRequest;
import home.work.booking.dto.UserResponse;
import home.work.booking.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    Mono<UserResponse> get(@PathVariable Long id) {
        return userService.get(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    Flux<UserResponse> list() {
        return userService.getUsers();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    Mono<UserResponse> create(@RequestBody UserRequest user) {
        return userService.register(user);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    Mono<UserResponse> update(@PathVariable Long id, @RequestBody UserRequest user) {
        return userService.update(id, user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return userService.delete(id).then(Mono.just(ResponseEntity.noContent().build()));
    }
}
