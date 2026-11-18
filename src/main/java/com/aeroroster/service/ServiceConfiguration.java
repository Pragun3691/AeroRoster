package com.aeroroster.service;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfiguration {
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
