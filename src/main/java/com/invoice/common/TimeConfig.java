package com.invoice.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a {@link Clock} bean so time-dependent logic (e.g. "today" on the
 * dashboard) is testable. Tests can supply a fixed Clock to exercise the
 * IST day boundary (00:00 IST == 18:30 UTC the previous day).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
