package com.fery.microservices.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RefreshTokenServiceTest {

    private final RefreshTokenService refreshTokenService = new RefreshTokenService();

    @Test
    void shouldGenerateSecureRefreshToken() {
        String token = refreshTokenService.generateToken();

        assertNotNull(token);
        assertFalse(token.isBlank());

        // 32 bytes encoded as Base64 URL-safe without padding = 43 characters
        assertEquals(43, token.length());
        assertTrue(token.matches("^[A-Za-z0-9_-]+$"));
    }

    @Test
    void shouldGenerateDifferentTokenEveryTime() {
        String firstToken = refreshTokenService.generateToken();
        String secondToken = refreshTokenService.generateToken();

        assertNotEquals(firstToken, secondToken);
    }

    @Test
    void shouldGenerateConsistentSha256Hash() {
        String token = refreshTokenService.generateToken();

        String firstHash = refreshTokenService.hashToken(token);
        String secondHash = refreshTokenService.hashToken(token);

        assertEquals(firstHash, secondHash);

        // SHA-256 produces 32 bytes = 64 hexadecimal characters
        assertEquals(64, firstHash.length());
        assertTrue(firstHash.matches("[a-f0-9]{64}"));

        assertNotEquals(token, firstHash);
    }

    @Test
    void shouldGenerateDifferentHashForDifferentTokens() {
        String firstToken = refreshTokenService.generateToken();
        String secondToken = refreshTokenService.generateToken();

        String firstHash = refreshTokenService.hashToken(firstToken);
        String secondHash = refreshTokenService.hashToken(secondToken);

        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void shouldRejectNullTokenWhenHashing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService.hashToken(null)
        );
    }

    @Test
    void shouldRejectBlankTokenWhenHashing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService.hashToken(" ")
        );
    }
}
