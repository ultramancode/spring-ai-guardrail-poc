package com.ultramancode.aiguardrail.global.web.config;

import com.ultramancode.aiguardrail.common.web.config.WebFilterOrder;
import com.ultramancode.aiguardrail.global.web.filter.HeaderBasedIdentityFilter;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterRegistrationConfig {

    @Bean
    public HeaderBasedIdentityFilter headerBasedIdentityFilter(Tracer tracer) {
        return new HeaderBasedIdentityFilter(tracer);
    }

    @Bean
    public FilterRegistrationBean<HeaderBasedIdentityFilter> headerBasedIdentityFilterRegistration(
            HeaderBasedIdentityFilter filter
    ) {
        FilterRegistrationBean<HeaderBasedIdentityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("headerBasedIdentityFilter");
        registration.setOrder(WebFilterOrder.HEADER_BASED_IDENTITY);
        return registration;
    }
}
