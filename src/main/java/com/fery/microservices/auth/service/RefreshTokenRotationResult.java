package com.fery.microservices.auth.service;

import com.fery.microservices.auth.entity.User;

public record RefreshTokenRotationResult(
        String refreshToken,
        User user
) {}
