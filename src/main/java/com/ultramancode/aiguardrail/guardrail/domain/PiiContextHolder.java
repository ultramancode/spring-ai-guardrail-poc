package com.ultramancode.aiguardrail.guardrail.domain;

/**
 * Manages the PiiContext using ThreadLocal to ensure request-scoped isolation.
 * <p>
 * Warning: ThreadLocal context may be lost in Reactive (WebFlux) or Virtual Thread environments.
 * In such cases, consider using Reactor Context.
 */
public class PiiContextHolder {
    private static final ThreadLocal<PiiContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(PiiContext context) {
        CONTEXT.set(context);
    }

    public static PiiContext getContext() {
        PiiContext context = CONTEXT.get();
        if (context == null) {
            context = new PiiContext();
            CONTEXT.set(context);
        }
        return context;
    }

    public static void clearContext() {
        CONTEXT.remove();
    }
}
