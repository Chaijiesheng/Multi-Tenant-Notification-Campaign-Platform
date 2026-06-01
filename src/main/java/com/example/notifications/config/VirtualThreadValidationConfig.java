package com.example.notifications.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Logs virtual-thread status at startup for operational validation.
 * Actual enforcement is handled by spring.threads.virtual.enabled=true in application.yml,
 * which wires TomcatProtocolHandlerCustomizer to use virtual threads for request handling.
 */
@Configuration
@Slf4j
public class VirtualThreadValidationConfig {

    @Bean
    public ApplicationRunner validateVirtualThreads() {
        return args -> {
            Thread current = Thread.currentThread();
            log.info("Startup thread type: isVirtual={} name={}",
                    current.isVirtual(), current.getName());

            boolean virtualEnabled = Boolean.parseBoolean(
                    System.getProperty("spring.threads.virtual.enabled", "false"));
            log.info("spring.threads.virtual.enabled system property: {}", virtualEnabled);
        };
    }
}
