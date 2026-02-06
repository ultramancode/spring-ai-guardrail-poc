package com.ultramancode.aiguardrail.observability.infrastructure.config;

import com.ultramancode.aiguardrail.observability.infrastructure.filter.GenAiContentObservationFilter;
import io.micrometer.observation.ObservationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservationFilter genAiContentObservationFilter() {
        return new GenAiContentObservationFilter();
    }


}
