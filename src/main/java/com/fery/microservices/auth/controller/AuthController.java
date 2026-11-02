package com.fery.microservices.auth.controller;

import com.fery.microservices.auth.dto.AuthResponse;
import com.fery.microservices.auth.dto.LoginRequest;
import com.fery.microservices.auth.dto.RegisterRequest;
import com.fery.microservices.auth.dto.UserResponse;
import com.fery.microservices.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }


    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request
            ) {
        UserResponse userResponse = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userResponse);
    }


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt) {

        return Map.of(
                "userId", Objects.requireNonNull(jwt.getSubject()),
                "email", Objects.requireNonNull(jwt.getClaimAsString("email")),
                "role", Objects.requireNonNull(jwt.getClaimAsString("roles"))
        );
    }
}
