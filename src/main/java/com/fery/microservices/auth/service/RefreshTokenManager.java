package com.fery.microservices.auth.service;

import com.fery.microservices.auth.entity.RefreshToken;
import com.fery.microservices.auth.entity.User;
import com.fery.microservices.auth.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class RefreshTokenManager {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final long refreshTokenTtlSeconds;

    public RefreshTokenManager(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenService refreshTokenService,
            @Value("${security.refresh-token-ttl:2592000}")
            long refreshTokenTtlSeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = refreshTokenService.generateToken();
        String tokenHash = refreshTokenService.hashToken(rawToken);

        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken refreshToken = RefreshToken.create(
                user,
                tokenHash,
                UUID.randomUUID(),
                now.plusSeconds(refreshTokenTtlSeconds)
        );

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String rotateRefreshToken(String rawToken) {
        return rotateRefreshTokenWithUser(rawToken).refreshToken();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RefreshTokenRotationResult rotateRefreshTokenWithUser(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw unauthorized();
        }

        String tokenHash = refreshTokenService.hashToken(rawToken);
        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::unauthorized);

        OffsetDateTime now = OffsetDateTime.now();

        // Detect reuse of an already revoked token.
        if (currentToken.isRevoked()) {
            refreshTokenRepository.revokeAllByTokenFamily(
                    currentToken.getTokenFamily(),
                    now
            );
            throw unauthorized();
        }

        if (!currentToken.getExpiresAt().isAfter(now)) {
            throw unauthorized();
        }

        User user = currentToken.getUser();

        String newRawToken = refreshTokenService.generateToken();
        String newTokenHash = refreshTokenService.hashToken(newRawToken);

        RefreshToken replacement = RefreshToken.create(
                user,
                newTokenHash,
                currentToken.getTokenFamily(),
                now.plusSeconds(refreshTokenTtlSeconds)
        );

        // Persist replacement first because the old token references it.
        RefreshToken saveReplacement =
                refreshTokenRepository.saveAndFlush(replacement);

        currentToken.revoke(now);
        currentToken.markReplacedBy(saveReplacement);
        refreshTokenRepository.save(currentToken);
        return new RefreshTokenRotationResult(newRawToken, currentToken.getUser());
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = refreshTokenService.hashToken(rawToken);
        refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .ifPresent(token -> token.revoke(OffsetDateTime.now()));
    }

    @Transactional
    public int cleanupExpiredTokens() {
        return refreshTokenRepository.deleteExpiredTokens(
                OffsetDateTime.now()
        );
    }


    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "invalid or expired refresh token"
        );
    }
}
