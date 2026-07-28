package com.emp.management.service;

import com.emp.management.dto.LoginRequest;
import com.emp.management.dto.LoginResponse;
import com.emp.management.dto.RegisterRequest;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.User;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.UserRepository;
import com.emp.management.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final PortalPermissionService portalPermissionService;

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = tokenProvider.generateToken(auth);
        String refreshToken = tokenProvider.generateRefreshToken(request.getEmail());

        user.setRefreshToken(passwordEncoder.encode(refreshToken)); // store hash, never plain text
        userRepository.save(user);

        EmployeeDetails emp = user.getEmployeeDetails();
        String fullName = emp != null ? emp.getFullName() : user.getEmail();

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .fullName(fullName)
                .allowedModules(user.getAllowedModules())
                .allowedPortals(portalPermissionService.getEffectiveAllowedPortals(user))
                .build();
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }
        validatePasswordStrength(request.getPassword());

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .active(true)
                .build();
        user = userRepository.save(user);

        EmployeeDetails.EmployeeDetailsBuilder empBuilder = EmployeeDetails.builder()
                .user(user)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .department(request.getDepartment())
                .position(request.getPosition())
                .hireDate(LocalDate.now())
                .active(true);

        if (request.getManagerId() != null) {
            EmployeeDetails manager = employeeDetailsRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager", request.getManagerId()));
            empBuilder.manager(manager);
        }

        employeeDetailsRepository.save(empBuilder.build());
    }

    @Transactional
    public LoginResponse refreshToken(String refreshToken) {
        // Extract email from the token regardless of expiry to locate the user
        String email = tokenProvider.getUsernameFromTokenIgnoreExpiry(refreshToken);
        if (email == null) throw new BadRequestException("Invalid refresh token");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Refresh token expired");
        }

        // Verify presented token matches the stored hash
        if (user.getRefreshToken() == null || !passwordEncoder.matches(refreshToken, user.getRefreshToken())) {
            throw new BadRequestException("Invalid refresh token");
        }

        String newAccessToken  = tokenProvider.generateToken(user.getEmail());
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        user.setRefreshToken(passwordEncoder.encode(newRefreshToken));
        userRepository.save(user);

        EmployeeDetails emp = user.getEmployeeDetails();
        String fullName = emp != null ? emp.getFullName() : user.getEmail();

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .fullName(fullName)
                .allowedModules(user.getAllowedModules())
                .allowedPortals(portalPermissionService.getEffectiveAllowedPortals(user))
                .build();
    }

    @Transactional(noRollbackFor = Exception.class)
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));
            userRepository.save(user);

            String resetUrl = baseUrl + "/reset-password?token=" + token;
            try {
                emailService.sendPasswordResetEmail(email, resetUrl);
            } catch (Exception e) {
                // Token is committed (noRollbackFor) — log the failure and surface a clear message
                log.error("Password reset token saved but email delivery failed for {}: {}", email, e.getMessage());
                throw new BadRequestException("Reset link created but email could not be sent. " +
                        "Please contact your administrator or try again.");
            }
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));

        if (user.getResetTokenExpiry() == null ||
                user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset link has expired. Please request a new one.");
        }

        validatePasswordStrength(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        user.setRefreshToken(null); // invalidate all existing sessions on password change
        userRepository.save(user);
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            throw new BadRequestException("Password must contain at least one uppercase letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            throw new BadRequestException("Password must contain at least one number");
        }
    }

    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setRefreshToken(null);
            userRepository.save(user);
        });
    }
}
