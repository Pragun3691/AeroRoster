package com.aeroroster.web.api;

import com.aeroroster.persistence.entity.CrewRole;
import com.aeroroster.service.CrewService;
import com.aeroroster.service.DutyAssignmentService;
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
@RequestMapping(value = "/api/v1/crew", produces = MediaType.APPLICATION_JSON_VALUE)
public class CrewController {
    private final CrewService crew;
    private final DutyAssignmentService duties;

    public CrewController(CrewService crew, DutyAssignmentService duties) {
        this.crew = crew;
        this.duties = duties;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CrewView> create(@Valid @RequestBody CrewRequest request) {
        CrewView result = crew.create(request);
        return ResponseEntity.created(URI.create("/api/v1/crew/" + result.id())).body(result);
    }

    @GetMapping("/{crewId}")
    public CrewView get(@PathVariable @Positive Long crewId) {
        return crew.get(crewId);
    }

    @GetMapping
    public PageResponse<CrewView> search(@RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) CrewRole role, @RequestParam(required = false) String active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return PageResponse.from(crew.search(new CrewFilter(q, role, ApiParameters.optionalBoolean(active, "active")),
                ApiParameters.page(page, size)));
    }

    @PutMapping(value = "/{crewId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CrewView update(@PathVariable @Positive Long crewId, @Valid @RequestBody CrewRequest request,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        return crew.update(crewId, request, ApiPreconditions.require(expectedVersion));
    }

    @PatchMapping(value = "/{crewId}/activity", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CrewView activity(@PathVariable @Positive Long crewId, @Valid @RequestBody ActivityRequest request,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        return crew.setActive(crewId, request.active(), ApiPreconditions.require(expectedVersion));
    }

    @GetMapping("/{crewId}/assignments")
    public PageResponse<AssignmentView> assignments(@PathVariable @Positive Long crewId,
            @RequestParam(defaultValue = "false") String activeOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        var paging = ApiParameters.page(page, size);
        return PageResponse.from(ApiParameters.optionalBoolean(activeOnly, "activeOnly")
                ? duties.activeForCrew(crewId, paging) : duties.historyForCrew(crewId, paging));
    }
}
