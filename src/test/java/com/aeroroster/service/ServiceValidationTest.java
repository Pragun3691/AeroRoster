package com.aeroroster.service;

import com.aeroroster.persistence.entity.CrewRole;
import com.aeroroster.service.dto.CrewRequest;
import com.aeroroster.service.exception.InvalidInputException;
import com.aeroroster.service.validation.ServiceValidation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class ServiceValidationTest {
    private static ValidatorFactory factory;
    private static ServiceValidation validation;

    @BeforeAll
    static void initializeValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validation = new ServiceValidation(factory.getValidator());
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    static Stream<CrewRequest> invalidCrew() {
        return Stream.of(
                new CrewRequest(" ", CrewRole.PILOT, "T22-P", 1, "9000000000"),
                new CrewRequest("Name", null, "T22-P", 1, "9000000000"),
                new CrewRequest("Name", CrewRole.PILOT, " ", 1, "9000000000"),
                new CrewRequest("Name", CrewRole.PILOT, "T22-P", null, "9000000000"),
                new CrewRequest("Name", CrewRole.PILOT, "T22-P", -1, "9000000000"),
                new CrewRequest("Name", CrewRole.PILOT, "T22-P", 1, "letters"),
                new CrewRequest("Name", CrewRole.PILOT, "T22-P", 1, ""),
                new CrewRequest("Name", CrewRole.PILOT, "T22-P", 1, "1     2"),
                new CrewRequest("Name", CrewRole.PILOT, "T22-P", 1, "1234567890123456"),
                new CrewRequest("N".repeat(101), CrewRole.PILOT, "T22-P", 1, "9000000000"));
    }

    @ParameterizedTest
    @MethodSource("invalidCrew")
    void rejectsEachInvalidFieldIndependently(CrewRequest request) {
        assertThrows(InvalidInputException.class, () -> validation.request(request));
    }
}
