package com.aeroroster.web.api;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.function.Function;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class ApiJsonConfiguration {
    @Bean
    public JsonMapperBuilderCustomizer strictApiJson() {
        SimpleModule dates = new SimpleModule("strict-api-dates");
        dates.addDeserializer(Instant.class, new IsoStringDeserializer<>(Instant.class,
                value -> OffsetDateTime.parse(value).toInstant()));
        dates.addDeserializer(LocalDate.class, new IsoStringDeserializer<>(LocalDate.class, LocalDate::parse));
        return builder -> builder.addModule(dates)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS,
                        DateTimeFeature.WRITE_UTC_AS_OFFSET);
    }

    private static final class IsoStringDeserializer<T> extends ValueDeserializer<T> {
        private final Class<T> type;
        private final Function<String, T> parse;

        private IsoStringDeserializer(Class<T> type, Function<String, T> parse) {
            this.type = type;
            this.parse = parse;
        }

        @Override
        public T deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(type, "Expected an ISO-8601 string");
            }
            try {
                return parse.apply(parser.getString());
            } catch (DateTimeException exception) {
                return context.reportInputMismatch(type, "Invalid ISO-8601 date or offset timestamp");
            }
        }
    }
}
