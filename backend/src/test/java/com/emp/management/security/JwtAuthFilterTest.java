package com.emp.management.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtAuthFilter is the last line of defense against a still-unexpired JWT whose
 * account was deactivated/locked mid-session (see 01-authentication.md TC-AUTH-036):
 * it re-checks isEnabled()/isAccountNonLocked() on every request rather than trusting
 * the token's own expiry alone.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtTokenProvider tokenProvider;
    @Mock private UserDetailsService userDetailsService;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void clearContextBefore() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContextAfter() {
        SecurityContextHolder.clearContext();
    }

    private UserDetails activeUser(String email) {
        return User.builder()
                .username(email).password("encoded")
                .authorities(List.of())
                .accountExpired(false).accountLocked(false).credentialsExpired(false).disabled(false)
                .build();
    }

    private UserDetails disabledUser(String email) {
        return User.builder()
                .username(email).password("encoded")
                .authorities(List.of())
                .accountExpired(false).accountLocked(false).credentialsExpired(false).disabled(true)
                .build();
    }

    private UserDetails lockedUser(String email) {
        return User.builder()
                .username(email).password("encoded")
                .authorities(List.of())
                .accountExpired(false).accountLocked(true).credentialsExpired(false).disabled(false)
                .build();
    }

    @Test
    void validToken_activeUser_setsAuthenticationInSecurityContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("valid-token")).thenReturn("alice@company.com");
        when(userDetailsService.loadUserByUsername("alice@company.com")).thenReturn(activeUser("alice@company.com"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice@company.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void deactivatedUsersStillValidToken_doesNotAuthenticate() throws Exception {
        // Token itself hasn't expired yet, but the account was deactivated after issuance.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer still-valid-but-stale");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("still-valid-but-stale")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("still-valid-but-stale")).thenReturn("bob@company.com");
        when(userDetailsService.loadUserByUsername("bob@company.com")).thenReturn(disabledUser("bob@company.com"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response); // chain still proceeds — downstream 401s it
    }

    @Test
    void lockedAccount_validToken_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("valid-token")).thenReturn("carol@company.com");
        when(userDetailsService.loadUserByUsername("carol@company.com")).thenReturn(lockedUser("carol@company.com"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidToken_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tampered-or-expired");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("tampered-or-expired")).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingAuthorizationHeader_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authorizationHeaderMissingBearerPrefix_isIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "raw-token-without-bearer-prefix");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).validateToken(anyString());
    }

    @Test
    void userDetailsServiceThrows_exceptionIsSwallowed_chainStillProceeds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("valid-token")).thenReturn("ghost@company.com");
        when(userDetailsService.loadUserByUsername("ghost@company.com"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("gone"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response); // never blocks the request pipeline
    }
}
