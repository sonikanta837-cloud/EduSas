package com.emp.management.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 15 * 60 * 1000L; // 15 minutes

    /** Max attempts per path (per IP). Login also adds per-email tracking. */
    private static final Map<String, Integer> PATH_LIMITS = Map.of(
        "/api/auth/login",           5,
        "/api/auth/forgot-password", 3,
        "/api/auth/refresh",         10
    );

    private record Attempts(int count, long windowStart) {}

    private final ConcurrentHashMap<String, Attempts> tracker = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();
        Integer limit = PATH_LIMITS.get(path);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip    = resolveIp(request);
        long   now   = Instant.now().toEpochMilli();
        String ipKey = path + ":ip:" + ip;

        if (isBlocked(ipKey, limit, now)) {
            log.warn("Rate limit exceeded for IP {} on {}", ip, path);
            sendTooManyRequests(response);
            return;
        }

        // For login: cache body so it can be re-read by Jackson downstream,
        // and check per-email rate limit before processing.
        byte[]          bodyBytes  = null;
        String          emailKey   = null;
        HttpServletRequest passRequest = request;

        if ("/api/auth/login".equals(path)) {
            bodyBytes = request.getInputStream().readAllBytes();
            String email = extractEmailFromJson(new String(bodyBytes, StandardCharsets.UTF_8));
            if (email != null) {
                emailKey = path + ":email:" + email.toLowerCase();
                if (isBlocked(emailKey, limit, now)) {
                    log.warn("Rate limit exceeded for email {} on {}", email, path);
                    sendTooManyRequests(response);
                    return;
                }
            }
            passRequest = new CachedBodyRequestWrapper(request, bodyBytes);
        }

        StatusCapturingWrapper wrapped = new StatusCapturingWrapper(response);
        chain.doFilter(passRequest, wrapped);

        int     status = wrapped.getStatus();
        boolean failed = status == 401 || status == 403;

        if (failed) {
            increment(ipKey, now);
            if (emailKey != null) increment(emailKey, now);
        } else if (status == 200) {
            tracker.remove(ipKey);
            if (emailKey != null) tracker.remove(emailKey);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isBlocked(String key, int limit, long now) {
        Attempts a = tracker.get(key);
        if (a == null) return false;
        if (now - a.windowStart() >= WINDOW_MS) { tracker.remove(key); return false; }
        return a.count() >= limit;
    }

    private void increment(String key, long now) {
        tracker.merge(key, new Attempts(1, now), (old, fresh) ->
            (now - old.windowStart() < WINDOW_MS)
                ? new Attempts(old.count() + 1, old.windowStart())
                : new Attempts(1, now)
        );
    }

    private void sendTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"status\":429,\"message\":\"Too many attempts. Please try again after 15 minutes.\"}"
        );
    }

    private String resolveIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    /** Extracts the email field value from a JSON body without a full JSON parser. */
    private String extractEmailFromJson(String body) {
        int idx = body.indexOf("\"email\"");
        if (idx == -1) return null;
        int colon  = body.indexOf(':', idx + 7);
        if (colon == -1) return null;
        int quote1 = body.indexOf('"', colon + 1);
        if (quote1 == -1) return null;
        int quote2 = body.indexOf('"', quote1 + 1);
        if (quote2 == -1) return null;
        String email = body.substring(quote1 + 1, quote2).trim();
        return email.isEmpty() ? null : email;
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    /** Wraps the request and replays cached body bytes so downstream can read the stream. */
    private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public int read() { return bais.read(); }
                public boolean isFinished() { return bais.available() == 0; }
                public boolean isReady() { return true; }
                public void setReadListener(ReadListener rl) {}
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    /** Captures the HTTP response status so we can decide whether to increment the counter. */
    private static class StatusCapturingWrapper extends HttpServletResponseWrapper {
        private int status = 200;

        StatusCapturingWrapper(HttpServletResponse response) { super(response); }

        @Override public void setStatus(int sc) { this.status = sc; super.setStatus(sc); }
        @Override public void sendError(int sc) throws IOException { this.status = sc; super.sendError(sc); }
        @Override public void sendError(int sc, String msg) throws IOException { this.status = sc; super.sendError(sc, msg); }
        @Override public int getStatus() { return status; }
    }
}
