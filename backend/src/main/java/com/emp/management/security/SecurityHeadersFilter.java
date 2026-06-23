package com.emp.management.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        // Prevent MIME-type sniffing (browser must honour declared Content-Type)
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Deny embedding in iframes — protects against clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // Content Security Policy
        // 'unsafe-inline' is required for React's injected styles / MUI emotion; tighten when a nonce is added.
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
            "font-src 'self' https://fonts.gstatic.com data:; " +
            "img-src 'self' data: blob:; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none';"
        );

        // Do not send the full referrer to cross-origin destinations
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Disable unused browser features
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");

        // HSTS — enable only when the app is served over HTTPS
        // response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        chain.doFilter(request, response);
    }
}
