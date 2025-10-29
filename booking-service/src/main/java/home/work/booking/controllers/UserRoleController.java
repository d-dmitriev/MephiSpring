package home.work.booking.controllers;

import home.work.booking.dto.UserRoleResponse;
import home.work.booking.services.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserRoleController {
    private final UserRoleService userRoleService;

    @GetMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    Flux<UserRoleResponse> userRoles(@PathVariable Long userId) {
        return userRoleService.roles(userId);
    }
}
