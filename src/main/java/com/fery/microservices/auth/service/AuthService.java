package com.fery.microservices.auth.service;

import com.fery.microservices.auth.dto.AuthResponse;
import com.fery.microservices.auth.dto.LoginRequest;
import com.fery.microservices.auth.dto.RegisterRequest;
import com.fery.microservices.auth.dto.UserResponse;
import com.fery.microservices.auth.entity.User;
import com.fery.microservices.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenManager refreshTokenManager;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenManager refreshTokenManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenManager = refreshTokenManager;
    }


    public UserResponse register(RegisterRequest request) {
        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        String fullName = request.getFullName().trim();
        String password = request.getPassword();

        validatePasswordLength(password);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        User user = User.create(
                email,
                passwordEncoder.encode(password),
                fullName
        );

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return toUserResponse(savedUser);
        } catch (DataIntegrityViolationException exception) {
            // Handles concurrent registration attempts with the same email.
            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Email is already registered"
                );
            }

            throw exception;
        }
    }


    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        String password = request.getPassword();

        validatePasswordMaximumLength(password);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        if (!user.isEnabled() || !passwordEncoder.matches(
                request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenManager.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .build();
    }


    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private void validatePasswordLength(String password) {
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes < 8 || passwordBytes > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be between 8 and 72 bytes in UTF-8 encoding"
            );
        }
    }

    private void validatePasswordMaximumLength(String password) {
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must not exceed 72 bytes in UTF-8 encoding"
            );
        }
    }
}
