package com.fery.microservices.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordConfigTest {

    private final PasswordEncoder passwordEncoder =
            new PasswordConfig().passwordEncoder();

    @Test
    void shouldEncodePasswordUsingBCrypt() {
        String rawPassword = "StrongPass123!";

        String encodedPassword =
                passwordEncoder.encode(rawPassword);

        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(encodedPassword.startsWith("$2"));
        assertTrue(passwordEncoder.matches(
                rawPassword,
                encodedPassword
        ));
    }

    @Test
    void shouldGenerateDifferentHashForSamePassword() {
        String password = "StrongPass123!";

        String firstHash = passwordEncoder.encode(password);
        String secondHash = passwordEncoder.encode(password);

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordEncoder.matches(password, firstHash));
        assertTrue(passwordEncoder.matches(password, secondHash));
    }
}