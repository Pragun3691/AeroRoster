package com.aeroroster.web.api;

import com.aeroroster.service.AircraftService;
import com.aeroroster.service.FlightService;
import com.aeroroster.service.dto.*;
import com.aeroroster.web.api.dto.ActivityRequest;
import com.aeroroster.web.api.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/aircraft", produces = MediaType.APPLICATION_JSON_VALUE)
public class AircraftController {
    private final AircraftService aircraft;
    private final FlightService flights;

    public AircraftController(AircraftService aircraft, FlightService flights) {
        this.aircraft = aircraft;
        this.flights = flights;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AircraftView> register(@Valid @RequestBody AircraftRequest request) {
        AircraftView result = aircraft.register(request);
        return ResponseEntity.created(URI.create("/api/v1/aircraft/" + result.id())).body(result);
    }

    @GetMapping("/{aircraftId}")
    public AircraftView get(@PathVariable @Positive Long aircraftId) {
        return aircraft.get(aircraftId);
    }

    @GetMapping
    public PageResponse<AircraftView> search(@RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) String active, @RequestParam(required = false) @Positive Long aircraftTypeId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return PageResponse.from(aircraft.search(new AircraftFilter(q, ApiParameters.optionalBoolean(active, "active"), aircraftTypeId),
                ApiParameters.page(page, size)));
    }

    @PutMapping(value = "/{aircraftId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AircraftView update(@PathVariable @Positive Long aircraftId, @Valid @RequestBody AircraftRequest request,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        return aircraft.update(aircraftId, request, ApiPreconditions.require(expectedVersion));
    }

    @PatchMapping(value = "/{aircraftId}/activity", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AircraftView activity(@PathVariable @Positive Long aircraftId, @Valid @RequestBody ActivityRequest request,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        return aircraft.setActive(aircraftId, request.active(), ApiPreconditions.require(expectedVersion));
    }

    @GetMapping("/{aircraftId}/flights")
    public PageResponse<FlightView> flights(@PathVariable @Positive Long aircraftId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return PageResponse.from(flights.forAircraft(aircraftId, ApiParameters.page(page, size)));
    }
}
