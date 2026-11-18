package com.aeroroster.service;

import java.time.Clock;
import com.aeroroster.domain.scheduling.SchedulingPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SchedulingPolicy.class)
public class ServiceConfiguration {
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
