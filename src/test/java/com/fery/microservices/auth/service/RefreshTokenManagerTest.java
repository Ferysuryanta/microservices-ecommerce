package com.fery.microservices.auth.service;

import com.fery.microservices.auth.entity.RefreshToken;
import com.fery.microservices.auth.entity.User;
import com.fery.microservices.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenManagerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    private RefreshTokenManager refreshTokenManager;

    private User user;

    @BeforeEach
    void setup() {
        refreshTokenManager = new RefreshTokenManager(
                refreshTokenRepository,
                refreshTokenService,
                3600
        );

        user = mock(User.class);
    }

    @Test
    void shouldCreateRefreshToken() {
        when(refreshTokenService.generateToken())
                .thenReturn("raw-token");

        when(refreshTokenService.hashToken("raw-token"))
                .thenReturn("hashed-token");

        String result = refreshTokenManager.createRefreshToken(user);

        assertEquals("raw-token", result);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken savedToken = captor.getValue();
        assertEquals("hashed-token", savedToken.getTokenHash());
        assertEquals(user, savedToken.getUser());
        assertNotNull(savedToken.getTokenFamily());
        assertTrue(savedToken.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    void shouldRotateValidRefreshToken() {
        UUID tokenFamily = UUID.randomUUID();

        RefreshToken currentToken = RefreshToken.create(
                user,
                "old-hash",
                tokenFamily,
                OffsetDateTime.now().plusHours(1)
        );

        when(refreshTokenService.hashToken("old-token"))
                .thenReturn("old-hash");
        when(refreshTokenService.generateToken())
                .thenReturn("new-token");
        when(refreshTokenService.hashToken("new-token"))
                .thenReturn("new-hash");

        when(refreshTokenRepository.findByTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.of(currentToken));

        when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String result = refreshTokenManager.rotateRefreshToken("old-token");

        assertEquals("new-token", result);
        assertTrue(currentToken.isRevoked());
        assertNotNull(currentToken.getReplacedBy());

        assertEquals(
                "new-hash",
                currentToken.getReplacedBy().getTokenHash()
        );

        assertEquals(
                tokenFamily,
                currentToken.getReplacedBy().getTokenFamily()
        );

        verify(refreshTokenRepository)
                .findByTokenHashForUpdate("old-hash");

        verify(refreshTokenRepository)
                .saveAndFlush(any(RefreshToken.class));

        verify(refreshTokenRepository)
                .save(currentToken);
        verify(refreshTokenService).hashToken("old-token");
        verify(refreshTokenService).hashToken("new-token");
        verify(refreshTokenService, times(2)).hashToken(anyString());
        verify(refreshTokenService).generateToken();
    }

    @Test
    void shouldRejectUnknownRefreshToken() {
        when(refreshTokenService.hashToken("unknown-token"))
                .thenReturn("unknown-hash");

        when(refreshTokenRepository.findByTokenHashForUpdate("unknown-hash"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refreshTokenManager.rotateRefreshToken("unknown-token")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void shouldRejectExpiredRefreshToken() {
        RefreshToken expiredToken = RefreshToken.create(
                user,
                "expired-hash",
                UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(1)
        );

        when(refreshTokenService.hashToken("expired-token"))
                .thenReturn("expired-hash");

        when(refreshTokenRepository.findByTokenHashForUpdate("expired-hash"))
                .thenReturn(Optional.of(expiredToken));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refreshTokenManager.rotateRefreshToken("expired-token")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verify(refreshTokenService, never()).generateToken();
    }

    @Test
    void shouldRevokeEntireTokenFamilyWhenRevokedTokenIsReused() {
        UUID tokenFamily = UUID.randomUUID();

        RefreshToken revokedToken = RefreshToken.create(
                user,
                "revoked-hash",
                tokenFamily,
                OffsetDateTime.now().plusHours(1)
        );

        revokedToken.revoke(OffsetDateTime.now().minusMinutes(1));

        when(refreshTokenService.hashToken("reused-token"))
                .thenReturn("revoked-hash");

        when(refreshTokenRepository.findByTokenHashForUpdate("revoked-hash"))
                .thenReturn(Optional.of(revokedToken));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refreshTokenManager.rotateRefreshToken("reused-token")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verify(refreshTokenRepository).revokeAllByTokenFamily(
                eq(tokenFamily),
                any(OffsetDateTime.class)
        );

        verify(refreshTokenService, never()).generateToken();
    }

    @Test
    void shouldRevokeExistingRefreshToken() {
        RefreshToken token = RefreshToken.create(
                user,
                "token-hash",
                UUID.randomUUID(),
                OffsetDateTime.now().plusHours(1)
        );

        when(refreshTokenService.hashToken("raw-token"))
                .thenReturn("token-hash");

        when(refreshTokenRepository.findByTokenHashForUpdate("token-hash"))
                .thenReturn(Optional.of(token));

        refreshTokenManager.revokeRefreshToken("raw-token");

        assertTrue(token.isRevoked());
    }

    @Test
    void shouldIgnoreBlankRefreshTokenWhenRevoking() {
        refreshTokenManager.revokeRefreshToken(" ");

        verifyNoInteractions(refreshTokenService);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void shouldCleanupExpiredTokens() {
        when(refreshTokenRepository.deleteExpiredTokens(
                any(OffsetDateTime.class)
        )).thenReturn(5);

        int deletedCount = refreshTokenManager.cleanupExpiredTokens();

        assertEquals(5, deletedCount);

        verify(refreshTokenRepository)
                .deleteExpiredTokens(any(OffsetDateTime.class));
    }
}
