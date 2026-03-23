package com.ultramancode.aiguardrail.common.web.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.Ordered;

/**
 * 전역 서블릿 필터의 실행 순서를 중앙에서 관리합니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WebFilterOrder {

    public static final int PII_CONTEXT_CLEANUP = Ordered.HIGHEST_PRECEDENCE;
    public static final int HEADER_BASED_IDENTITY = Ordered.HIGHEST_PRECEDENCE + 10;
}
