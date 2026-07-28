package com.emp.management.service;

import com.emp.management.dto.LoginRequest;
import com.emp.management.dto.LoginResponse;
import com.emp.management.dto.RegisterRequest;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.UserRepository;
import com.emp.management.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;

    @InjectMocks private AuthService authService;

    private User testUser;
    private EmployeeDetails testEmployee;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("alice@company.com")
                .password("encodedPwd")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        testEmployee = EmployeeDetails.builder()
                .id(1L)
                .user(testUser)
                .firstName("Alice")
                .lastName("Smith")
                .active(true)
                .build();

        testUser.setEmployeeDetails(testEmployee);
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsTokens() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@company.com");
        req.setPassword("password");

        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken(auth)).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken("alice@company.com")).thenReturn("refresh-token");
        when(userRepository.save(any())).thenReturn(testUser);

        LoginResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getEmail()).isEqualTo("alice@company.com");
        assertThat(response.getRole()).isEqualTo("EMPLOYEE");
    }

    @Test
    void login_badCredentials_throwsException() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@company.com");
        req.setPassword("wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_userNotFoundAfterAuth_throwsResourceNotFoundException() {
        LoginRequest req = new LoginRequest();
        req.setEmail("ghost@company.com");
        req.setPassword("password");

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("ghost@company.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Register ─────────────────────────────────────────────────────────────

    @Test
    void register_newEmail_createsUserAndEmployee() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("Password1");
        req.setFirstName("Bob");
        req.setLastName("Jones");
        req.setRole(Role.EMPLOYEE);

        when(userRepository.existsByEmail("new@company.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(testUser);
        when(employeeDetailsRepository.save(any())).thenReturn(testEmployee);

        authService.register(req);

        verify(userRepository).save(any(User.class));
        verify(employeeDetailsRepository).save(any(EmployeeDetails.class));
    }

    @Test
    void register_duplicateEmail_throwsBadRequestException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@company.com");
        req.setPassword("Password1");
        req.setFirstName("Alice");
        req.setLastName("Smith");
        req.setRole(Role.EMPLOYEE);

        when(userRepository.existsByEmail("alice@company.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void register_withManagerId_setsManager() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("Password1");
        req.setFirstName("Bob");
        req.setLastName("Jones");
        req.setRole(Role.EMPLOYEE);
        req.setManagerId(2L);

        EmployeeDetails manager = EmployeeDetails.builder().id(2L).build();

        when(userRepository.existsByEmail("new@company.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(testUser);
        when(employeeDetailsRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(employeeDetailsRepository.save(any())).thenReturn(testEmployee);

        authService.register(req);

        verify(employeeDetailsRepository).findById(2L);
    }

    // ── Refresh Token ────────────────────────────────────────────────────────

    @Test
    void refreshToken_validToken_returnsNewTokens() {
        testUser.setRefreshToken("stored-hash");

        when(tokenProvider.getUsernameFromTokenIgnoreExpiry("old-refresh")).thenReturn("alice@company.com");
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tokenProvider.validateToken("old-refresh")).thenReturn(true);
        when(passwordEncoder.matches("old-refresh", "stored-hash")).thenReturn(true);
        when(tokenProvider.generateToken("alice@company.com")).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken("alice@company.com")).thenReturn("new-refresh");
        when(passwordEncoder.encode("new-refresh")).thenReturn("new-hash");
        when(userRepository.save(any())).thenReturn(testUser);

        LoginResponse response = authService.refreshToken("old-refresh");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refreshToken_invalidToken_throwsBadRequestException() {
        testUser.setRefreshToken(null);

        when(tokenProvider.getUsernameFromTokenIgnoreExpiry("bad-token")).thenReturn("alice@company.com");
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tokenProvider.validateToken("bad-token")).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshToken("bad-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void refreshToken_expiredToken_throwsBadRequestException() {
        testUser.setRefreshToken("stored-hash");

        when(tokenProvider.getUsernameFromTokenIgnoreExpiry("expired-refresh")).thenReturn("alice@company.com");
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tokenProvider.validateToken("expired-refresh")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("expired-refresh"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    // ── Forgot Password ──────────────────────────────────────────────────────

    @Test
    void forgotPassword_knownEmail_savesResetTokenAndSendsEmail() {
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString());

        authService.forgotPassword("alice@company.com");

        verify(userRepository).save(testUser);
        verify(emailService).sendPasswordResetEmail(eq("alice@company.com"), anyString());
        assertThat(testUser.getResetToken()).isNotNull();
        assertThat(testUser.getResetTokenExpiry()).isAfter(LocalDateTime.now());
    }

    @Test
    void forgotPassword_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("nobody@example.com");

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    // ── Reset Password ───────────────────────────────────────────────────────

    @Test
    void resetPassword_validToken_updatesPassword() {
        testUser.setResetToken("valid-token");
        testUser.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));

        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPassword1")).thenReturn("encodedNew");
        when(userRepository.save(any())).thenReturn(testUser);

        authService.resetPassword("valid-token", "newPassword1");

        assertThat(testUser.getResetToken()).isNull();
        assertThat(testUser.getResetTokenExpiry()).isNull();
        verify(passwordEncoder).encode("newPassword1");
    }

    @Test
    void resetPassword_expiredToken_throwsBadRequestException() {
        testUser.setResetToken("expired-token");
        testUser.setResetTokenExpiry(LocalDateTime.now().minusHours(2));

        when(userRepository.findByResetToken("expired-token")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.resetPassword("expired-token", "newPassword"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resetPassword_unknownToken_throwsBadRequestException() {
        when(userRepository.findByResetToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "newPassword"))
                .isInstanceOf(BadRequestException.class);
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @Test
    void logout_knownEmail_clearsRefreshToken() {
        testUser.setRefreshToken("some-refresh");
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        authService.logout("alice@company.com");

        assertThat(testUser.getRefreshToken()).isNull();
    }

    @Test
    void logout_unknownEmail_doesNotThrow() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        authService.logout("nobody@example.com");

        verify(userRepository, never()).save(any());
    }
}
