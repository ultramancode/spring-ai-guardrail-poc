package com.ultramancode.aiguardrail.guardrail.infrastructure.config;

import com.ultramancode.aiguardrail.common.web.config.WebFilterOrder;
import com.ultramancode.aiguardrail.guardrail.domain.PiiContextStore;
import com.ultramancode.aiguardrail.guardrail.infrastructure.filter.PiiContextCleanupFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PiiContextFilterConfig {

    @Bean
    public PiiContextCleanupFilter piiContextCleanupFilter(PiiContextStore piiContextStore) {
        return new PiiContextCleanupFilter(piiContextStore);
    }

    @Bean
    public FilterRegistrationBean<PiiContextCleanupFilter> piiContextCleanupFilterRegistration(
            PiiContextCleanupFilter filter
    ) {
        FilterRegistrationBean<PiiContextCleanupFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("piiContextCleanupFilter");
        registration.setOrder(WebFilterOrder.PII_CONTEXT_CLEANUP);
        return registration;
    }
}
