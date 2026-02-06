package com.ultramancode.aiguardrail.global;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HeaderBasedIdentityFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_SESSION_ID = "X-Session-Id";

    private final Tracer tracer;

    // PoC: enrich traces with user/session from request headers (X-User-Id, X-Session-Id)
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String userId = firstNonBlank(request.getHeader(HEADER_USER_ID), "admin-test");
        String sessionId = firstNonBlank(request.getHeader(HEADER_SESSION_ID), "session-test");

        var span = tracer.currentSpan();
        if (span != null) {
            span.tag("user.id", userId);
            span.tag("session.id", sessionId);
            span.tag("poc.identity.source", "header");
        }

        filterChain.doFilter(request, response);
    }

    private String firstNonBlank(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed;
    }
}
