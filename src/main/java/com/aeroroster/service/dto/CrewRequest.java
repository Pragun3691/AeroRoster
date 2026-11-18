package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.CrewRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import com.aeroroster.service.validation.InputValues;

public record CrewRequest(@NotBlank @Size(max = 100) String name,
        @NotNull CrewRole role,
        @NotBlank @Size(max = 30) String licenseNo,
        @NotNull @PositiveOrZero Integer experienceYears,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "(?=(?:[^0-9]*[0-9]){7,15}[^0-9]*$)\\+?[0-9][0-9 ()-]*[0-9]") String contactNo) {
    public CrewRequest {
        name = InputValues.text(name);
        licenseNo = InputValues.identifier(licenseNo);
        contactNo = InputValues.text(contactNo);
    }
}
