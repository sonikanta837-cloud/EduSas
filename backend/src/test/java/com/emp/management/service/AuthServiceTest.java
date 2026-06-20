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
    @Mock private TimesheetService timesheetService;
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
        when(passwordEncoder.encode("refresh-token")).thenReturn("encoded-refresh");
        when(userRepository.save(any())).thenReturn(testUser);
        doNothing().when(timesheetService).recordLogin(anyLong());

        LoginResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getEmail()).isEqualTo("alice@company.com");
        assertThat(response.getRole()).isEqualTo("EMPLOYEE");
        verify(timesheetService).recordLogin(1L);
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

    @Test
    void login_employeeWithNoDetails_usesEmailAsFullName() {
        testUser.setEmployeeDetails(null);
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@company.com");
        req.setPassword("password");

        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken(auth)).thenReturn("access");
        when(tokenProvider.generateRefreshToken("alice@company.com")).thenReturn("refresh");
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(testUser);
        doNothing().when(timesheetService).recordLogin(anyLong());

        LoginResponse response = authService.login(req);

        assertThat(response.getFullName()).isEqualTo("alice@company.com");
    }

    // ── Register ─────────────────────────────────────────────────────────────

    @Test
    void register_newEmail_createsUserAndEmployee() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("ValidPass1");
        req.setFirstName("Bob");
        req.setLastName("Jones");
        req.setRole(Role.EMPLOYEE);

        when(userRepository.existsByEmail("new@company.com")).thenReturn(false);
        when(passwordEncoder.encode("ValidPass1")).thenReturn("encoded");
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
        req.setPassword("pass");
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
        req.setPassword("ValidPass1");
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

    @Test
    void register_passwordTooShort_throwsBadRequestException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("short");
        req.setFirstName("New");
        req.setLastName("User");
        req.setRole(Role.EMPLOYEE);

        when(userRepository.existsByEmail("new@company.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("8 characters");
    }

    @Test
    void register_passwordNoUppercase_throwsBadRequestException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("alllowercase1");
        req.setFirstName("New");
        req.setLastName("User");
        req.setRole(Role.EMPLOYEE);

        when(userRepository.existsByEmail("new@company.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("uppercase");
    }

    @Test
    void register_passwordNoDigit_throwsBadRequestException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("NoDigitsHere");
        req.setFirstName("New");
        req.setLastName("User");
        req.setRole(Role.EMPLOYEE);

        when(userRepository.existsByEmail("new@company.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("number");
    }

    @Test
    void register_managerNotFound_throwsResourceNotFoundException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@company.com");
        req.setPassword("ValidPass1");
        req.setFirstName("Bob");
        req.setLastName("Jones");
        req.setRole(Role.EMPLOYEE);
        req.setManagerId(999L);

        when(userRepository.existsByEmail("new@company.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(testUser);
        when(employeeDetailsRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Refresh Token ────────────────────────────────────────────────────────

    @Test
    void refreshToken_validToken_returnsNewTokens() {
        testUser.setRefreshToken("hashed-old-refresh");

        when(tokenProvider.getUsernameFromTokenIgnoreExpiry("old-refresh")).thenReturn("alice@company.com");
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tokenProvider.validateToken("old-refresh")).thenReturn(true);
        when(passwordEncoder.matches("old-refresh", "hashed-old-refresh")).thenReturn(true);
        when(tokenProvider.generateToken("alice@company.com")).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken("alice@company.com")).thenReturn("new-refresh");
        when(passwordEncoder.encode("new-refresh")).thenReturn("hashed-new-refresh");
        when(userRepository.save(any())).thenReturn(testUser);

        LoginResponse response = authService.refreshToken("old-refresh");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refreshToken_invalidTokenNullEmail_throwsBadRequestException() {
        when(tokenProvider.getUsernameFromTokenIgnoreExpiry("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> authService.refreshToken("bad-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void refreshToken_expiredToken_throwsBadRequestException() {
        when(tokenProvider.getUsernameFromTokenIgnoreExpiry("expired-refresh")).thenReturn("alice@company.com");
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tokenProvider.validateToken("expired-refresh")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("expired-refresh"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refreshToken_hashMismatch_throwsBadRequestException() {
        testUser.setRefreshToken("hashed-different");

        when(tokenProvider.getUsernameFromTokenIgnoreExpiry("valid-token")).thenReturn("alice@company.com");
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(passwordEncoder.matches("valid-token", "hashed-different")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("valid-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void refreshToken_noStoredRefreshToken_throwsBadRequestException() {
        testUser.setRefreshToken(null);

        when(tokenProvider.getUsernameFromTokenIgnoreExpiry("valid-token")).thenReturn("alice@company.com");
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshToken("valid-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid refresh token");
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

        // Should NOT throw — silent for security
        authService.forgotPassword("nobody@example.com");

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    // ── Reset Password ───────────────────────────────────────────────────────

    @Test
    void resetPassword_validToken_updatesPassword() {
        testUser.setResetToken("valid-token");
        testUser.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));

        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("NewPass1")).thenReturn("encodedNew");
        when(userRepository.save(any())).thenReturn(testUser);

        authService.resetPassword("valid-token", "NewPass1");

        assertThat(testUser.getResetToken()).isNull();
        assertThat(testUser.getResetTokenExpiry()).isNull();
        assertThat(testUser.getRefreshToken()).isNull();
        verify(passwordEncoder).encode("NewPass1");
    }

    @Test
    void resetPassword_expiredToken_throwsBadRequestException() {
        testUser.setResetToken("expired-token");
        testUser.setResetTokenExpiry(LocalDateTime.now().minusHours(2));

        when(userRepository.findByResetToken("expired-token")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.resetPassword("expired-token", "NewPass1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resetPassword_unknownToken_throwsBadRequestException() {
        when(userRepository.findByResetToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "NewPass1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resetPassword_weakPassword_throwsBadRequestException() {
        testUser.setResetToken("valid-token");
        testUser.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));

        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.resetPassword("valid-token", "weak"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resetPassword_nullExpiry_throwsBadRequestException() {
        testUser.setResetToken("valid-token");
        testUser.setResetTokenExpiry(null);

        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.resetPassword("valid-token", "NewPass1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @Test
    void logout_knownEmail_clearsRefreshToken() {
        testUser.setRefreshToken("some-refresh");
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);
        doNothing().when(timesheetService).recordLogout(anyString());

        authService.logout("alice@company.com");

        assertThat(testUser.getRefreshToken()).isNull();
        verify(timesheetService).recordLogout("alice@company.com");
    }

    @Test
    void logout_unknownEmail_doesNotThrow() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        doNothing().when(timesheetService).recordLogout(anyString());

        authService.logout("nobody@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void logout_callsTimesheetRecordLogout() {
        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);
        doNothing().when(timesheetService).recordLogout("alice@company.com");

        authService.logout("alice@company.com");

        verify(timesheetService).recordLogout("alice@company.com");
    }
}
