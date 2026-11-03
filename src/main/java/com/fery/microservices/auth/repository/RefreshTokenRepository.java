package com.fery.microservices.auth.repository;

import com.fery.microservices.auth.entity.RefreshToken;
import com.fery.microservices.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Find refresh token using its SHA-256 hash.
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Find all tokens belonging to a user
    List<RefreshToken> findAllByUser(User user);

    // Find all refresh tokens belonging to a token family.
    List<RefreshToken> findAllByTokenFamily(UUID tokenFamily);

    // Find active tokens belonging to a user
    List<RefreshToken> findAllByUserAndRevokedAtIsNullAndExpiresAtAfter(User user, OffsetDateTime now);

    // Delete expired tokens
    @Modifying
    @Query("""
        DELETE FROM RefreshToken rt
        WHERE rt.expiresAt < :now
    """)
    int deleteExpiredTokens(
            @Param("now")OffsetDateTime now
            );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
       UPDATE RefreshToken rt
       SET rt.revokedAt = :now
       WHERE rt.tokenFamily = :tokenFamily
       AND rt.revokedAt IS NULL
    """)
    int revokeAllByTokenFamily(
            @Param("tokenFamily") UUID tokenFamily,
            @Param("now") OffsetDateTime now
    );

    @EntityGraph(attributePaths = {"user"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT rt
        FROM RefreshToken  rt
        WHERE rt.tokenHash = :tokenHash
    """)
    Optional<RefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );
}
