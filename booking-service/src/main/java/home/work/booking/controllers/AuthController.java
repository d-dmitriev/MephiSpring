package home.work.booking.controllers;

import home.work.booking.dto.AuthRequest;
import home.work.booking.dto.AuthResponse;
import home.work.booking.services.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping
    public Mono<AuthResponse> login(@RequestBody AuthRequest request) {
        return authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
                )
                .map(auth -> {
                    UserDetails userDetails = (UserDetails) auth.getPrincipal();
                    String token = jwtService.generateToken(userDetails);
                    String refreshToken = jwtService.generateRefreshToken(userDetails);
                    return new AuthResponse(token, refreshToken, "Bearer");
                });
    }
}
