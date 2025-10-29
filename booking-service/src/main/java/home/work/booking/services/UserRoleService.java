package home.work.booking.services;

import home.work.booking.dto.UserRoleResponse;
import home.work.booking.exceptions.UserNotFoundException;
import home.work.booking.repositories.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class UserRoleService {
    private final UserRoleRepository userRoleRepository;

    public Flux<UserRoleResponse> roles(Long id) {
        return userRoleRepository.findAllByUserId(id)
                .switchIfEmpty(Flux.error(new UserNotFoundException(id)))
                .map(userRole ->
                        UserRoleResponse.builder().userId(userRole.getUserId()).role(userRole.getRole()).build()
                );
    }
}
