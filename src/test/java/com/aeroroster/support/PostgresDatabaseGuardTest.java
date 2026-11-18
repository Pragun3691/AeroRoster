package com.aeroroster.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class PostgresDatabaseGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:postgresql://localhost:5432/aeroroster_dev",
        "jdbc:postgresql://localhost:5432/postgres",
        "jdbc:postgresql://localhost:5432/aeroroster_test?currentSchema=public",
        "jdbc:h2:mem:aeroroster_test"
    })
    void rejectsOtherTargetsBeforeDatasourceInitialization(String url) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url", url));
            assertThrows(IllegalStateException.class, () -> new PostgresTest.DatabaseGuard().initialize(context));
        }
    }
}
