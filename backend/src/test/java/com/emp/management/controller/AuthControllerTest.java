package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.LoginRequest;
import com.emp.management.dto.LoginResponse;
import com.emp.management.dto.RegisterRequest;
import com.emp.management.entity.Role;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuthService authService;

    private LoginResponse buildLoginResponse() {
        return LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .userId(1L)
                .email("alice@company.com")
                .role("EMPLOYEE")
                .fullName("Alice Smith")
                .build();
    }

    // ── POST /api/auth/login ─────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@company.com");
        req.setPassword("password");

        when(authService.login(any())).thenReturn(buildLoginResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.email").value("alice@company.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void login_missingEmail_returns400() throws Exception {
        String body = "{\"password\":\"pass\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("not-an-email");
        req.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_serviceThrowsException_returns4xx() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@company.com");
        req.setPassword("wrong");

        when(authService.login(any())).thenThrow(new BadRequestException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/register ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void register_asAdmin_returns200() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("ValidPass1");
        req.setFirstName("New");
        req.setLastName("User");
        req.setRole(Role.EMPLOYEE);

        doNothing().when(authService).register(any());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string("Employee registered successfully"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void register_asEmployee_returns403() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("ValidPass1");
        req.setFirstName("New");
        req.setLastName("User");
        req.setRole(Role.EMPLOYEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_unauthenticated_returns401or403() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("ValidPass1");
        req.setFirstName("New");
        req.setLastName("User");
        req.setRole(Role.EMPLOYEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 401 && status != 403) {
                        throw new AssertionError("Expected 401 or 403, but got: " + status);
                    }
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void register_duplicateEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@company.com");
        req.setPassword("ValidPass1");
        req.setFirstName("Dup");
        req.setLastName("User");
        req.setRole(Role.EMPLOYEE);

        doThrow(new BadRequestException("Email already registered")).when(authService).register(any());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/refresh ───────────────────────────────────────────────

    @Test
    void refresh_validToken_returns200() throws Exception {
        when(authService.refreshToken("valid-refresh")).thenReturn(buildLoginResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .param("refreshToken", "valid-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void refresh_invalidToken_returns400() throws Exception {
        when(authService.refreshToken("bad-token")).thenThrow(new BadRequestException("Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .param("refreshToken", "bad-token"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/forgot-password ───────────────────────────────────────

    @Test
    void forgotPassword_knownEmail_returns200() throws Exception {
        doNothing().when(authService).forgotPassword("alice@company.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .param("email", "alice@company.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset email sent"));
    }

    @Test
    void forgotPassword_unknownEmail_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("No account found with that email"))
                .when(authService).forgotPassword("nobody@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .param("email", "nobody@example.com"))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/auth/reset-password ────────────────────────────────────────

    @Test
    void resetPassword_validToken_returns200() throws Exception {
        doNothing().when(authService).resetPassword("valid-token", "newPass123");

        mockMvc.perform(post("/api/auth/reset-password")
                        .param("token", "valid-token")
                        .param("newPassword", "newPass123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset successfully"));
    }

    @Test
    void resetPassword_expiredToken_returns400() throws Exception {
        doThrow(new BadRequestException("Reset link has expired"))
                .when(authService).resetPassword("expired-token", "newPass");

        mockMvc.perform(post("/api/auth/reset-password")
                        .param("token", "expired-token")
                        .param("newPassword", "newPass"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@company.com")
    void logout_authenticatedUser_returns200() throws Exception {
        doNothing().when(authService).logout("alice@company.com");

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(content().string("Logged out successfully"));
    }
}
