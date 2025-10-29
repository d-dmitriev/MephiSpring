package home.work.booking.services;

import home.work.booking.dto.UserRequest;
import home.work.booking.dto.UserResponse;
import home.work.booking.exceptions.UserNotFoundException;
import home.work.booking.entities.User;
import home.work.booking.repositories.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final DatabaseClient databaseClient;

    public Mono<UserResponse> get(Long id) {
        return userRepository
                .findByIdWithRoles(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .map(this::convertToDto);
    }

    public Mono<UserResponse> update(Long id, UserRequest updateRequest) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .flatMap(existingUser -> {
                    // Обновляем поля пользователя, если они предоставлены
                    if (updateRequest.getUsername() != null) {
                        existingUser.setUsername(updateRequest.getUsername());
                    }

                    if (updateRequest.getPassword() != null) {
                        existingUser.setPassword(BCrypt.hashpw(updateRequest.getPassword(), BCrypt.gensalt()));
                    }

                    Mono<Void> rolesUpdateMono = updateRequest.getRoles() != null ?
                            updateUserRoles(id, updateRequest.getRoles()) :
                            Mono.empty();

                    return userRepository.save(existingUser)
                            .then(rolesUpdateMono)
                            .then(userRepository.findByIdWithRoles(id))
                            .map(this::convertToDto);
                });
    }

    public Mono<Void> delete(Long id) {
        return userRepository.existsById(id)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new UserNotFoundException(id));
                    }
                    return userRepository.deleteById(id);
                });
    }

    public Mono<UserResponse> register(UserRequest user) {
        return userRepository.findByUsername(user.getUsername())
                .flatMap(existingUser ->
                        Mono.error(new RuntimeException("User already exists: " + user.getUsername()))
                )
                .switchIfEmpty(Mono.defer(() ->
                        userRepository.save(User
                                .builder()
                                .username(user.getUsername())
                                .password(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()))
                                .build()
                        ).flatMap(savedUser ->
                                saveUserRoles(savedUser.getId(), user.getRoles())
                                        .thenReturn(new UserWithRoles(savedUser, user.getRoles()))
                        )
                )).cast(UserWithRoles.class).flatMap(this::convertToDto);
    }

    private Mono<Void> saveUserRoles(Long userId, List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            // Если роли не указаны, назначаем роль USER по умолчанию
            roles = List.of("USER");
        }

        return Flux.fromIterable(roles)
                .flatMap(role ->
                        databaseClient.sql("INSERT INTO user_roles (user_id, role) VALUES (:userId, :role)")
                                .bind("userId", userId)
                                .bind("role", role)
                                .fetch()
                                .rowsUpdated()
                )
                .then();
    }

    private Mono<Void> updateUserRoles(Long userId, List<String> newRoles) {
        // Сначала удаляем старые роли, затем добавляем новые
        return databaseClient.sql("DELETE FROM user_roles WHERE user_id = :userId")
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .then(saveUserRoles(userId, newRoles));
    }

    public Flux<UserResponse> getUsers() {
        return userRepository.findAllWithRoles().map(this::convertToDto);
    }

    private Mono<UserResponse> convertToDto(UserWithRoles user) {
        return Mono.just(UserResponse
                .builder()
                .id(user.getUser().getId())
                .username(user.getUser().getUsername())
                .roles(String.join(",", user.getRoles()))
                .build());
    }

    private UserResponse convertToDto(UserRepository.UserWithRoles user) {
        return UserResponse
                .builder()
                .id(user.getId())
                .username(user.getUsername())
                .roles(user.getRoles())
                .build();
    }

    @Data
    @AllArgsConstructor
    private static class UserWithRoles {
        private User user;
        private List<String> roles;
    }
}
