package com.aeroroster.web.api.dto;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ExpiryUpdateRequest(@JsonProperty(value = "validUntil", required = true) LocalDate validUntil) {
}
