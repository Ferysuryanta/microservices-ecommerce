package com.fery.microservices.auth.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {


    private static final int TOKEN_BYTE_LENGTH = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure random refresh token.
     * The returned is URL-safe Base64 without padding.
     */

    public String generateToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    /**
     * Creates a SHA-256 hash of the raw refresh token.
     * Store this hash instead of the raw token.
     */

    public String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Refresh token must not be null or blank"
            );
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of()
                    .formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    ex
            );
        }
    }

}
