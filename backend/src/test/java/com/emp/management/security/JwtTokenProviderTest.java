package com.emp.management.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    private static final String SECRET = "ThisIsAVerySecureSecretKeyForJWTTokenGenerationMinimum256BitsLong";
    private static final long EXPIRATION = 86400000L;
    private static final long REFRESH_EXPIRATION = 604800000L;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", EXPIRATION);
        ReflectionTestUtils.setField(tokenProvider, "refreshExpiration", REFRESH_EXPIRATION);
    }

    @Test
    void generateToken_fromAuthentication_returnsValidToken() {
        Authentication auth = buildAuth("test@example.com");
        String token = tokenProvider.generateToken(auth);
        assertThat(token).isNotBlank();
    }

    @Test
    void generateToken_fromUsername_returnsValidToken() {
        String token = tokenProvider.generateToken("user@example.com");
        assertThat(token).isNotBlank();
    }

    @Test
    void generateRefreshToken_returnsValidToken() {
        String token = tokenProvider.generateRefreshToken("user@example.com");
        assertThat(token).isNotBlank();
    }

    @Test
    void getUsernameFromToken_returnsCorrectEmail() {
        String email = "user@example.com";
        String token = tokenProvider.generateToken(email);
        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo(email);
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = tokenProvider.generateToken("user@example.com");
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_malformedToken_returnsFalse() {
        assertThat(tokenProvider.validateToken("not.a.valid.token")).isFalse();
    }

    @Test
    void validateToken_emptyString_returnsFalse() {
        assertThat(tokenProvider.validateToken("")).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(expiredProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(expiredProvider, "jwtExpiration", -1000L);
        ReflectionTestUtils.setField(expiredProvider, "refreshExpiration", -1000L);
        String token = expiredProvider.generateToken("user@example.com");
        assertThat(expiredProvider.validateToken(token)).isFalse();
    }

    @Test
    void generateToken_fromAuthentication_subjectMatchesPrincipal() {
        Authentication auth = buildAuth("admin@company.com");
        String token = tokenProvider.generateToken(auth);
        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("admin@company.com");
    }

    @Test
    void refreshToken_differentFromAccessToken() {
        String access = tokenProvider.generateToken("user@example.com");
        String refresh = tokenProvider.generateRefreshToken("user@example.com");
        assertThat(access).isNotEqualTo(refresh);
    }

    private Authentication buildAuth(String email) {
        UserDetails user = new User(email, "password", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
