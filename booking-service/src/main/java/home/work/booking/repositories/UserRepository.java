package home.work.booking.repositories;

import home.work.booking.entities.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Transactional
@Repository
public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    Mono<User> findByUsername(String username);

    @Query("""
        SELECT u.id, u.username, u.password,
               GROUP_CONCAT(ur.role) as roles
        FROM users u
        LEFT JOIN user_roles ur ON u.id = ur.user_id
        WHERE u.id = :id
        GROUP BY u.id, u.username, u.password
        """)
    Mono<UserWithRoles> findByIdWithRoles(Long id);

    @Query("""
        SELECT u.id, u.username, u.password,
               GROUP_CONCAT(ur.role) as roles
        FROM users u
        LEFT JOIN user_roles ur ON u.id = ur.user_id
        WHERE u.username = :username
        GROUP BY u.id, u.username, u.password
        """)
    Mono<UserWithRoles> findByUsernameWithRoles(String username);

    @Query("""
        SELECT u.id, u.username, u.password,
               GROUP_CONCAT(ur.role) as roles
        FROM users u
        LEFT JOIN user_roles ur ON u.id = ur.user_id
        GROUP BY u.id, u.username, u.password
        """)
    Flux<UserWithRoles> findAllWithRoles();

    @Data
    @AllArgsConstructor
    class UserWithRoles {
        private Long id;
        private String username;
        private String password;
        private String roles;
    }
}
