package com.jobhunt.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    /** One injectable clock, so time-dependent logic can be tested with a fixed one. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
