
package com.fery.microservices.auth.controller;

import com.fery.microservices.auth.dto.AuthResponse;
import com.fery.microservices.auth.dto.LoginRequest;
import com.fery.microservices.auth.dto.RegisterRequest;
import com.fery.microservices.auth.dto.UserResponse;
import com.fery.microservices.auth.service.AuthService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerShouldReturn201() throws Exception {
        when(authService.register(any()))
                .thenReturn(new UserResponse(
                        UUID.randomUUID(),
                        "test@example.com",
                        "Test User",
                        "USER",
                        OffsetDateTime.now(ZoneOffset.UTC)
                ));

        String request = """
                {
                    "email": "test@example.com",
                    "password": "StrongPass123!",
                    "fullName": "Test User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void registerShouldRejectInvalidEmail() throws Exception {
        String request = """
                {
                    "email": "invalid-email",
                    "password": "StrongPass123!",
                    "fullName": "Test User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerShouldRejectShortPassword() throws Exception {
        String request = """
                {
                    "email": "test@example.com",
                    "password": "123",
                    "fullName": "Test User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginShouldReturn200() throws Exception {
        when(authService.login(any()))
                .thenReturn(AuthResponse.builder()
                        .accessToken("test-token")
                        .tokenType("Bearer")
                        .expiresIn(900L)
                        .build());

        String request = """
                {
                    "email": "test@example.com",
                    "password": "StrongPass123!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void registerShouldNotExposePasswordOrPasswordHash() throws Exception {
        UserResponse response = new UserResponse(
                UUID.randomUUID(),
                "test@example.com",
                "Test User",
                "USER",
                OffsetDateTime.parse("2026-10-30T08:00:00Z")
        );

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "test@example.com",
                                "password": "StrongPass123!",
                                "fullName": "Test User"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginShouldNotExposePasswordOrPasswordHash() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .accessToken("test-access-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "test@example.com",
                                "password": "StrongPass123!"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
