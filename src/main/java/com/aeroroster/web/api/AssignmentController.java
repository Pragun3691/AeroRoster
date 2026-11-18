package com.aeroroster.web.api;

import com.aeroroster.service.DutyAssignmentService;
import com.aeroroster.service.dto.*;
import com.aeroroster.web.api.dto.EmptyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/assignments", produces = MediaType.APPLICATION_JSON_VALUE)
public class AssignmentController {
    private final DutyAssignmentService duties;

    public AssignmentController(DutyAssignmentService duties) {
        this.duties = duties;
    }

    @PostMapping(value = "/eligibility", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EligibilityDecision eligibility(@Valid @RequestBody AssignmentRequest request) {
        return duties.check(request);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssignmentView> assign(@Valid @RequestBody AssignmentRequest request) {
        AssignmentView result = duties.assign(request);
        return ResponseEntity.created(URI.create("/api/v1/assignments/" + result.id())).body(result);
    }

    @GetMapping("/{assignmentId}")
    public AssignmentView get(@PathVariable @Positive Long assignmentId) {
        return duties.get(assignmentId);
    }

    @PutMapping("/{assignmentId}/cancellation")
    public AssignmentView cancel(@PathVariable @Positive Long assignmentId, @RequestBody(required = false) EmptyRequest ignored) {
        return duties.cancel(assignmentId);
    }

    @PostMapping("/{assignmentId}/reassignments")
    public ResponseEntity<AssignmentView> reassign(@PathVariable @Positive Long assignmentId,
            @RequestBody(required = false) EmptyRequest ignored) {
        AssignmentView result = duties.reassign(assignmentId);
        return ResponseEntity.created(URI.create("/api/v1/assignments/" + result.id())).body(result);
    }
}
