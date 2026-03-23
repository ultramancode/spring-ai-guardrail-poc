package com.ultramancode.aiguardrail.observability.infrastructure.config;

import com.ultramancode.aiguardrail.observability.infrastructure.filter.GenAiContentObservationFilter;
import io.micrometer.observation.ObservationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservationFilter genAiContentObservationFilter(
            @Value("${observability.capture-raw-content:false}") boolean captureRawContent,
            @Value("${observability.max-content-length:2000}") int maxContentLength
    ) {
        return new GenAiContentObservationFilter(captureRawContent, maxContentLength);
    }

}
