
package com.fery.microservices.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Setter
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Setter
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Setter
    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(nullable = false, length = 30)
    private String role = "USER";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public static User create(
            String email,
            String passwordHash,
            String fullName
    ) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFullName(fullName);
        return user;
    }
}
