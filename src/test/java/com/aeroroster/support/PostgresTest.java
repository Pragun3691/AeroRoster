package com.aeroroster.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.core.annotation.AliasFor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/aeroroster_test}",
    "spring.datasource.username=${TEST_DB_USER:postgres}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = PostgresTest.DatabaseGuard.class)
public @interface PostgresTest {
    @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;

    /** Reject a development URL before datasource and Flyway initialization. */
    class DatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            String url = context.getEnvironment().getRequiredProperty("spring.datasource.url");
            if (!url.matches("jdbc:postgresql://[^/]+/aeroroster_test")) {
                throw new IllegalStateException("Tests require a dedicated aeroroster_test database URL");
            }
        }
    }
}
