package com.aeroroster.web.api;

import com.aeroroster.persistence.entity.FlightStatus;
import com.aeroroster.service.FlightService;
import com.aeroroster.service.DutyAssignmentService;
import com.aeroroster.service.dto.*;
import com.aeroroster.web.api.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/flights", produces = MediaType.APPLICATION_JSON_VALUE)
public class FlightController {
    private final FlightService flights;
    private final DutyAssignmentService duties;

    public FlightController(FlightService flights, DutyAssignmentService duties) {
        this.flights = flights;
        this.duties = duties;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FlightView> create(@Valid @RequestBody FlightRequest request) {
        FlightView result = flights.create(request);
        return ResponseEntity.created(URI.create("/api/v1/flights/" + result.id())).body(result);
    }

    @GetMapping("/{flightId}")
    public FlightView get(@PathVariable @Positive Long flightId) {
        return flights.get(flightId);
    }

    @GetMapping
    public PageResponse<FlightView> search(@RequestParam(required = false) @Size(max = 200) String flightNumber,
            @RequestParam(required = false) FlightStatus status, @RequestParam(required = false) @Positive Long aircraftId,
            @RequestParam(required = false) String departureFrom, @RequestParam(required = false) String departureUntil,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        FlightFilter filter = new FlightFilter(flightNumber, status, aircraftId,
                ApiParameters.optionalInstant(departureFrom, "departureFrom"),
                ApiParameters.optionalInstant(departureUntil, "departureUntil"));
        return PageResponse.from(flights.search(filter, ApiParameters.page(page, size)));
    }

    @PutMapping(value = "/{flightId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FlightView update(@PathVariable @Positive Long flightId, @Valid @RequestBody FlightRequest request,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        return flights.update(flightId, request, ApiPreconditions.require(expectedVersion));
    }

    @PatchMapping(value = "/{flightId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FlightView status(@PathVariable @Positive Long flightId, @Valid @RequestBody FlightStatusRequest request,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        return flights.changeStatus(flightId, request.status(), ApiPreconditions.require(expectedVersion));
    }

    @PutMapping("/{flightId}/cancellation")
    public FlightView cancel(@PathVariable @Positive Long flightId, @RequestBody(required = false) EmptyRequest ignored,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        return flights.cancel(flightId, ApiPreconditions.require(expectedVersion));
    }

    @GetMapping("/{flightId}/assignments")
    public PageResponse<AssignmentView> assignments(@PathVariable @Positive Long flightId,
            @RequestParam(defaultValue = "true") String activeOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        var paging = ApiParameters.page(page, size);
        return PageResponse.from(ApiParameters.optionalBoolean(activeOnly, "activeOnly")
                ? duties.forFlight(flightId, paging) : duties.historyForFlight(flightId, paging));
    }

    @GetMapping("/{flightId}/coverage")
    public FlightCrewCoverage coverage(@PathVariable @Positive Long flightId) {
        return duties.coverage(flightId);
    }
}
