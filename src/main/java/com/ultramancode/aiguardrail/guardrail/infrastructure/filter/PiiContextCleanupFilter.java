package com.ultramancode.aiguardrail.guardrail.infrastructure.filter;

import com.ultramancode.aiguardrail.guardrail.domain.PiiContextStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class PiiContextCleanupFilter extends OncePerRequestFilter {

    private final PiiContextStore piiContextStore;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            piiContextStore.clear();
        }
    }
}
