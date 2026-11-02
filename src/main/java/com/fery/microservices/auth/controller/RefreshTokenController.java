package com.fery.microservices.auth.controller;

import com.fery.microservices.auth.dto.RefreshTokenRequest;
import com.fery.microservices.auth.dto.RefreshTokenResponse;
import com.fery.microservices.auth.service.JwtService;
import com.fery.microservices.auth.service.RefreshTokenManager;
import com.fery.microservices.auth.service.RefreshTokenRotationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(
        name = "Refresh Token",
        description = "Refresh Token rotation and logout APIs"
)
public class RefreshTokenController {

    private final RefreshTokenManager refreshTokenManager;
    private final JwtService jwtService;

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Rotates the refresh token and issues a new access token."
    )
    @ApiResponses ({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<RefreshTokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenRotationResult result =
                refreshTokenManager.rotateRefreshTokenWithUser(request.getRefreshToken());

        String accessToken = jwtService.generateAccessToken(result.user());

        RefreshTokenResponse response = RefreshTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(result.refreshToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .build();
        return ResponseEntity.ok(response);
    }
}
