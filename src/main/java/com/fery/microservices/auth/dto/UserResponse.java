package com.fery.microservices.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        OffsetDateTime createdAt
) {
}
