package com.ultramancode.aiguardrail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiGuardrailApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiGuardrailApplication.class, args);
    }

}
