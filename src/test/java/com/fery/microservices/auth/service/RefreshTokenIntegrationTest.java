
package com.fery.microservices.auth.service;

import com.fery.microservices.auth.entity.RefreshToken;
import com.fery.microservices.auth.entity.User;
import com.fery.microservices.auth.repository.RefreshTokenRepository;
import com.fery.microservices.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenIntegrationTest {

    @Autowired
    private RefreshTokenManager refreshTokenManager;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create(
                "integration-" + UUID.randomUUID() + "@example.com",
                "test-password-hash",
                "Integration Test User"
        );

        user = userRepository.saveAndFlush(user);
    }

    @Test
    void shouldCreateAndPersistRefreshToken() {
        String rawToken = refreshTokenManager.createRefreshToken(user);

        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken savedToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow();

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());
        assertNotEquals(rawToken, savedToken.getTokenHash());
        assertEquals(user.getId(), savedToken.getUser().getId());
        assertFalse(savedToken.isRevoked());
        assertTrue(savedToken.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    void shouldRotateRefreshTokenSuccessfully() {
        String oldRawToken = refreshTokenManager.createRefreshToken(user);
        String oldHash = refreshTokenService.hashToken(oldRawToken);

        String newRawToken =
                refreshTokenManager.rotateRefreshToken(oldRawToken);

        String newHash = refreshTokenService.hashToken(newRawToken);

        RefreshToken oldToken = refreshTokenRepository
                .findByTokenHash(oldHash)
                .orElseThrow();

        RefreshToken newToken = refreshTokenRepository
                .findByTokenHash(newHash)
                .orElseThrow();

        assertNotEquals(oldRawToken, newRawToken);
        assertTrue(oldToken.isRevoked());
        assertNotNull(oldToken.getReplacedBy());
        assertEquals(newToken.getId(), oldToken.getReplacedBy().getId());
        assertEquals(
                oldToken.getTokenFamily(),
                newToken.getTokenFamily()
        );
        assertFalse(newToken.isRevoked());
    }


    @Test
    void shouldRejectReuseOfRevokedRefreshToken() {
        String oldRawToken = refreshTokenManager.createRefreshToken(user);
        String oldHash = refreshTokenService.hashToken(oldRawToken);

        // First rotation: old token becomes revoked.
        refreshTokenManager.rotateRefreshToken(oldRawToken);

        // Second rotation using the old token must be rejected.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refreshTokenManager.rotateRefreshToken(oldRawToken)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        RefreshToken oldToken = refreshTokenRepository
                .findByTokenHash(oldHash)
                .orElseThrow();

        assertTrue(oldToken.isRevoked());
        assertNotNull(oldToken.getReplacedBy());

        // Get replacement ID without initializing the lazy proxy.
        UUID replacementId = oldToken.getReplacedBy().getId();

        // Fetch replacement independently from the repository.
        RefreshToken replacement = refreshTokenRepository
                .findById(replacementId)
                .orElseThrow();

        assertTrue(replacement.isRevoked());
    }

    @Test
    void shouldRejectUnknownRefreshToken() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refreshTokenManager.rotateRefreshToken(
                        "unknown-refresh-token"
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
    }


    @Test
    void shouldRejectExpiredRefreshToken() {
        String rawToken = "expired-refresh-token-" + UUID.randomUUID();
        String expiredHash = refreshTokenService.hashToken(rawToken);

        RefreshToken expiredToken = RefreshToken.create(
                user,
                expiredHash,
                UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(5)
        );

        refreshTokenRepository.saveAndFlush(expiredToken);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refreshTokenManager.rotateRefreshToken(rawToken)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
    }

    @Test
    void shouldRevokeRefreshTokenSuccessfully() {
        String rawToken = refreshTokenManager.createRefreshToken(user);
        String tokenHash = refreshTokenService.hashToken(rawToken);

        refreshTokenManager.revokeRefreshToken(rawToken);

        RefreshToken revokedToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow();

        assertTrue(revokedToken.isRevoked());
    }

    @Test
    void shouldCleanupExpiredTokens() {
        String expiredHash = refreshTokenService.hashToken(
                "cleanup-token-" + UUID.randomUUID()
        );

        RefreshToken expiredToken = RefreshToken.create(
                user,
                expiredHash,
                UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(10)
        );

        refreshTokenRepository.saveAndFlush(expiredToken);

        int deletedCount = refreshTokenManager.cleanupExpiredTokens();

        assertTrue(deletedCount >= 1);
        assertTrue(
                refreshTokenRepository.findByTokenHash(expiredHash).isEmpty()
        );
    }
}
