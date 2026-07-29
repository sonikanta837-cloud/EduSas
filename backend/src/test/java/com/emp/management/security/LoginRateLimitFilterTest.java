package com.emp.management.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LoginRateLimitFilter keeps its sliding-window attempt counters in an in-memory
 * map scoped to the filter instance, so each test reuses one instance across
 * several simulated requests to accumulate (or reset) state, mirroring how the
 * real filter behaves across a sequence of live HTTP calls.
 */
class LoginRateLimitFilterTest {

    private LoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter();
    }

    private MockHttpServletRequest postRequest(String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr(ip);
        return request;
    }

    private MockHttpServletRequest loginRequest(String ip, String email) {
        MockHttpServletRequest request = postRequest("/api/auth/login", ip);
        String body = email != null
                ? "{\"email\":\"" + email + "\",\"password\":\"whatever\"}"
                : "{\"password\":\"whatever\"}";
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private FilterChain chainRespondingWith(int status) {
        return (ServletRequest req, ServletResponse res) -> ((HttpServletResponse) res).setStatus(status);
    }

    // ── Path scoping ──────────────────────────────────────────────────────────

    @Test
    void nonLimitedPath_alwaysPassesThrough_regardlessOfAttemptCount() throws ServletException, IOException {
        MockHttpServletRequest request = postRequest("/api/employees", "10.0.0.1");

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chainRespondingWith(401));
            assertThat(response.getStatus()).isEqualTo(401); // never 429 — path isn't rate-limited
        }
    }

    @Test
    void getRequest_bypassesRateLimiting_evenOnLimitedPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.setRemoteAddr("10.0.0.2");

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chainRespondingWith(401));
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    // ── Per-IP blocking ───────────────────────────────────────────────────────

    @Test
    void loginPath_fifthFailedAttempt_stillPassesThrough_sixthIsBlocked() throws ServletException, IOException {
        String ip = "10.0.0.3";
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(loginRequest(ip, null), response, chainRespondingWith(401));
            assertThat(response.getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse sixthResponse = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest(ip, null), sixthResponse, chainRespondingWith(401));

        assertThat(sixthResponse.getStatus()).isEqualTo(429);
        assertThat(sixthResponse.getContentAsString()).contains("Too many attempts");
    }

    @Test
    void forgotPasswordPath_limitIsThree_fourthAttemptIsBlocked() throws ServletException, IOException {
        String ip = "10.0.0.4";
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(postRequest("/api/auth/forgot-password", ip), response, chainRespondingWith(401));
        }

        MockHttpServletResponse fourthResponse = new MockHttpServletResponse();
        filter.doFilterInternal(postRequest("/api/auth/forgot-password", ip), fourthResponse, chainRespondingWith(401));

        assertThat(fourthResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void refreshPath_limitIsTen_eleventhAttemptIsBlocked() throws ServletException, IOException {
        String ip = "10.0.0.5";
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(postRequest("/api/auth/refresh", ip), response, chainRespondingWith(401));
        }

        MockHttpServletResponse eleventhResponse = new MockHttpServletResponse();
        filter.doFilterInternal(postRequest("/api/auth/refresh", ip), eleventhResponse, chainRespondingWith(401));

        assertThat(eleventhResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void non401Or403Status_doesNotCountTowardTheLimit() throws ServletException, IOException {
        // Only 401/403 responses are treated as "failed" attempts; a 400 (e.g. bad
        // request body) must not silently exhaust the same-IP quota.
        String ip = "10.0.0.8";
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(postRequest("/api/auth/refresh", ip), response, chainRespondingWith(400));
            assertThat(response.getStatus()).isEqualTo(400);
        }

        MockHttpServletResponse eleventh = new MockHttpServletResponse();
        filter.doFilterInternal(postRequest("/api/auth/refresh", ip), eleventh, chainRespondingWith(400));

        assertThat(eleventh.getStatus()).isEqualTo(400); // not blocked
    }

    // ── Success resets the counter ───────────────────────────────────────────

    @Test
    void successfulLogin_resetsFailureCounter_forSameIp() throws ServletException, IOException {
        String ip = "10.0.0.6";
        for (int i = 0; i < 4; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(loginRequest(ip, null), response, chainRespondingWith(401));
        }

        MockHttpServletResponse successResponse = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest(ip, null), successResponse, chainRespondingWith(200));
        assertThat(successResponse.getStatus()).isEqualTo(200);

        // A subsequent failure is NOT blocked, proving the prior counter was cleared
        MockHttpServletResponse nextResponse = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest(ip, null), nextResponse, chainRespondingWith(401));

        assertThat(nextResponse.getStatus()).isEqualTo(401);
    }

    // ── Per-email blocking (independent of IP) ───────────────────────────────

    @Test
    void loginPath_perEmailBlocking_blocksAcrossDifferentIps() throws ServletException, IOException {
        String email = "victim@company.com";
        for (int i = 0; i < 5; i++) {
            // A different source IP each time so only the email-based counter accumulates
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(loginRequest("10.1.0." + i, email), response, chainRespondingWith(401));
        }

        MockHttpServletResponse sixthResponse = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest("10.1.0.99", email), sixthResponse, chainRespondingWith(401));

        assertThat(sixthResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void loginPath_malformedBodyWithoutEmailField_doesNotThrow_stillTracksByIp() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(loginRequest("10.0.0.7", null), response, chainRespondingWith(401));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ── X-Forwarded-For is trusted for IP resolution (see TC-SEC-019) ────────

    @Test
    void loginPath_ipResolvedFromXForwardedForHeader_whenPresent() throws ServletException, IOException {
        String spoofedIp = "203.0.113.9";
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = loginRequest("real-remote-addr-irrelevant", null);
            request.addHeader("X-Forwarded-For", spoofedIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chainRespondingWith(401));
        }

        MockHttpServletRequest sixth = loginRequest("real-remote-addr-irrelevant", null);
        sixth.addHeader("X-Forwarded-For", spoofedIp);
        MockHttpServletResponse sixthResponse = new MockHttpServletResponse();
        filter.doFilterInternal(sixth, sixthResponse, chainRespondingWith(401));

        // Documents current behavior: the filter trusts a client-supplied
        // X-Forwarded-For header for IP resolution with no trusted-proxy check.
        // An attacker varying this header per request could equally bypass the
        // limiter entirely — this test records the existing behavior, not an
        // endorsement of it (see 15-security.md TC-SEC-019).
        assertThat(sixthResponse.getStatus()).isEqualTo(429);
    }
}
