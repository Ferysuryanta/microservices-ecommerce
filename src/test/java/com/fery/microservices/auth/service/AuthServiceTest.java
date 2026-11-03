
package com.fery.microservices.auth.service;

import com.fery.microservices.auth.dto.LoginRequest;
import com.fery.microservices.auth.dto.RegisterRequest;
import com.fery.microservices.auth.entity.User;
import com.fery.microservices.auth.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenManager refreshTokenManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerShouldCreateUserSuccessfully() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(" TEST@example.com ");
        request.setPassword("StrongPass123!");
        request.setFullName(" Fery Sembiring ");

        when(userRepository.existsByEmailIgnoreCase("test@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("StrongPass123!"))
                .thenReturn("encoded-password");

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(request);

        assertNotNull(response);

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).saveAndFlush(captor.capture());

        User savedUser = captor.getValue();

        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("Fery Sembiring", savedUser.getFullName());
        assertEquals("encoded-password", savedUser.getPasswordHash());

        verify(passwordEncoder).encode("StrongPass123!");
    }

    @Test
    void registerShouldRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("StrongPass123!");
        request.setFullName("Fery Sembiring");

        when(userRepository.existsByEmailIgnoreCase("test@example.com"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerShouldRejectPasswordExceeding15Bytes() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("A".repeat(73));
        request.setFullName("Fery Sembiring");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerShouldRejectPasswordBelow8Bytes() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");
        request.setPassword("Short7!");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void loginShouldGenerateTokenSuccessfully() {
        LoginRequest request = new LoginRequest();
        request.setEmail(" TEST@example.com ");
        request.setPassword("StrongPass123!");

        User user = mock(User.class);

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isEnabled()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("encoded-password");

        when(passwordEncoder.matches(
                "StrongPass123!", "encoded-password"
        )).thenReturn(true);

        when(jwtService.generateAccessToken(user))
                .thenReturn("test-jwt-token");

        when(jwtService.getAccessTokenExpirySeconds())
                .thenReturn(900L);

        when(refreshTokenManager.createRefreshToken(user))
                .thenReturn("test-refresh-token");

        var response = authService.login(request);

        assertNotNull(response);

        verify(jwtService).generateAccessToken(user);
        verify(refreshTokenManager).createRefreshToken(user);
    }

    @Test
    void loginShouldRejectInvalidPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("WrongPass123");

        User user = mock(User.class);

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isEnabled()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("encoded-password");

        when(passwordEncoder.matches(
                "WrongPass123", "encoded-password"
        )).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void loginShouldRejectDisabledUser() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("StrongPass123!");

        User user = mock(User.class);

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(user));

        when(user.isEnabled()).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void loginShouldRejectPasswordExceeding72Bytes() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("A".repeat(73));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerShouldRejectConcurrentDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");
        request.setPassword("StrongPass123!");

        when(userRepository.existsByEmailIgnoreCase("test@example.com"))
                .thenReturn(false, true);

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate email"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
