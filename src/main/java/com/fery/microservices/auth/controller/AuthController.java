package com.fery.microservices.auth.controller;

import com.fery.microservices.auth.dto.AuthResponse;
import com.fery.microservices.auth.dto.LoginRequest;
import com.fery.microservices.auth.dto.RegisterRequest;
import com.fery.microservices.auth.dto.UserResponse;
import com.fery.microservices.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@Tag(
        name = "Authentication",
        description = "APIs for user registration, login, and authentication"
)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }


    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account using email, password, and full name."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "user registered successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email already registered"
            )
    })
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request
            ) {
        UserResponse userResponse = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userResponse);
    }


    @Operation(
            summary = "User login",
            description = "Authenticates user credentials and returns an access token."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid email or password"
            )
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }

    @Operation(
            summary = "Get current authenticated user",
            description = "Returns the identity and role of the authenticated user.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User information retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid JWT token"
            )
    })
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
