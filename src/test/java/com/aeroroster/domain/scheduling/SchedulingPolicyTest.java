package com.aeroroster.domain.scheduling;

import com.aeroroster.service.ServiceConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingPolicyTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(ServiceConfiguration.class);

    @Test
    void bindsIllustrativeDefaultsAndSupportsOverrides() {
        context.run(application -> {
            SchedulingPolicy policy = application.getBean(SchedulingPolicy.class);
            assertEquals(Duration.ofHours(1), policy.preflightBuffer());
            assertEquals(Duration.ofMinutes(30), policy.postflightBuffer());
            assertEquals(Duration.ofHours(10), policy.minimumRest());
            assertEquals(Duration.ofHours(10), policy.maximumRolling24Hours());
            assertEquals(Duration.ofHours(40), policy.maximumRolling7Days());
            assertEquals(Duration.ofHours(14), policy.maximumUtcDay());
        });
        context.withPropertyValues("aeroroster.scheduling.minimum-rest=PT0S",
                "aeroroster.scheduling.maximum-rolling24-hours=PT12H").run(application -> {
                    assertEquals(Duration.ZERO, application.getBean(SchedulingPolicy.class).minimumRest());
                    assertEquals(Duration.ofHours(12), application.getBean(SchedulingPolicy.class).maximumRolling24Hours());
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "aeroroster.scheduling.minimum-rest=-PT1H",
        "aeroroster.scheduling.preflight-buffer=-PT1H",
        "aeroroster.scheduling.postflight-buffer=PT0.000000001S",
        "aeroroster.scheduling.maximum-rolling24-hours=PT0S",
        "aeroroster.scheduling.maximum-rolling7-days=-PT1H",
        "aeroroster.scheduling.maximum-utc-day=PT0S"
    })
    void invalidPolicyFailsStartup(String property) {
        context.withPropertyValues(property).run(application -> assertNotNull(application.getStartupFailure()));
    }
}
