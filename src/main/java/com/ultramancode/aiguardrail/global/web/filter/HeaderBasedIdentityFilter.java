package com.ultramancode.aiguardrail.global.web.filter;

import com.ultramancode.aiguardrail.common.util.StringValueUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class HeaderBasedIdentityFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_SESSION_ID = "X-Session-Id";

    private final Tracer tracer;

    // PoC: HTTP 요청 헤더(X-User-Id, X-Session-Id)에서 사용자 및 세션 정보를 추출하여 관측성 트레이스(trace)에 추가합니다.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String userId = StringValueUtils.firstNonBlank(request.getHeader(HEADER_USER_ID), "admin-test");
        String sessionId = StringValueUtils.firstNonBlank(request.getHeader(HEADER_SESSION_ID), "session-test");

        Span span = tracer.currentSpan();
        if (span != null) {
            span.tag("user.id", userId);
            span.tag("session.id", sessionId);
            span.tag("poc.identity.source", "header");
        }

        filterChain.doFilter(request, response);
    }
}
