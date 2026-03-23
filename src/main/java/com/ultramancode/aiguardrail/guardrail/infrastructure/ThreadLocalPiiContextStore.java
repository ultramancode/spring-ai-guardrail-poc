package com.ultramancode.aiguardrail.guardrail.infrastructure;

import com.ultramancode.aiguardrail.guardrail.domain.PiiContext;
import com.ultramancode.aiguardrail.guardrail.domain.PiiContextStore;
import org.springframework.stereotype.Component;

/**
 * ThreadLocal을 사용하여 요청 스코프(Request-scoped) 격리를 보장하면서 PiiContext를 관리하는 구현체입니다.
 */
@Component
public class ThreadLocalPiiContextStore implements PiiContextStore {
    private final ThreadLocal<PiiContext> context = new ThreadLocal<>();

    @Override
    public PiiContext get() {
        PiiContext ctx = context.get();
        if (ctx == null) {
            ctx = new PiiContext();
            context.set(ctx);
        }
        return ctx;
    }

    @Override
    public void clear() {
        context.remove();
    }
}
