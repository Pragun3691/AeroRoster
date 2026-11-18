package com.aeroroster.web.api;

import com.aeroroster.service.CrewUnavailabilityService;
import com.aeroroster.service.dto.*;
import com.aeroroster.web.api.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/crew/{crewId}/unavailability", produces = MediaType.APPLICATION_JSON_VALUE)
public class UnavailabilityController {
    private final CrewUnavailabilityService unavailable;

    public UnavailabilityController(CrewUnavailabilityService unavailable) {
        this.unavailable = unavailable;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UnavailabilityView> add(@PathVariable @Positive Long crewId,
            @Valid @RequestBody UnavailabilityRequest request) {
        UnavailabilityView result = unavailable.add(crewId, request);
        return ResponseEntity.created(URI.create("/api/v1/crew/" + crewId + "/unavailability/" + result.id())).body(result);
    }

    @GetMapping
    public PageResponse<UnavailabilityView> list(@PathVariable @Positive Long crewId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return PageResponse.from(unavailable.list(crewId, ApiParameters.page(page, size)));
    }

    @GetMapping("/{periodId}")
    public UnavailabilityView get(@PathVariable @Positive Long crewId, @PathVariable @Positive Long periodId) {
        return unavailable.get(crewId, periodId);
    }

    @PutMapping(value = "/{periodId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UnavailabilityView update(@PathVariable @Positive Long crewId, @PathVariable @Positive Long periodId,
            @Valid @RequestBody UnavailabilityRequest request,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        return unavailable.update(crewId, periodId, request, ApiPreconditions.require(expectedVersion));
    }

    @DeleteMapping("/{periodId}")
    public ResponseEntity<Void> remove(@PathVariable @Positive Long crewId, @PathVariable @Positive Long periodId,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        unavailable.remove(crewId, periodId, ApiPreconditions.require(expectedVersion));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conflicts")
    public UnavailabilityConflictView conflicts(@PathVariable @Positive Long crewId,
            @RequestParam String start, @RequestParam String end) {
        return new UnavailabilityConflictView(unavailable.conflicts(crewId,
                ApiParameters.optionalInstant(start, "start"), ApiParameters.optionalInstant(end, "end")));
    }
}
