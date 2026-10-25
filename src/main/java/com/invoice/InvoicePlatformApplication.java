package com.invoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the API process.
 *
 * <p>The API, the worker (Phase 2), and the scheduler (Phase 2) are separate entry points
 * that share this same codebase. Only this class boots the HTTP API.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class InvoicePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoicePlatformApplication.class, args);
    }
}
