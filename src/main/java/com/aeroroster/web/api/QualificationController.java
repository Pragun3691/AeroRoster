package com.aeroroster.web.api;

import com.aeroroster.service.QualificationService;
import com.aeroroster.service.dto.*;
import com.aeroroster.web.api.dto.ExpiryUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/crew/{crewId}/qualifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class QualificationController {
    private final QualificationService qualifications;

    public QualificationController(QualificationService qualifications) {
        this.qualifications = qualifications;
    }

    @GetMapping
    public List<CrewQualificationView> list(@PathVariable @Positive Long crewId) {
        return qualifications.forCrew(crewId);
    }

    @GetMapping("/{qualificationId}")
    public CrewQualificationView get(@PathVariable @Positive Long crewId, @PathVariable @Positive Long qualificationId) {
        return qualifications.getForCrew(crewId, qualificationId);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CrewQualificationView> grant(@PathVariable @Positive Long crewId,
            @Valid @RequestBody QualificationGrantRequest request) {
        CrewQualificationView result = qualifications.grant(crewId, request);
        return ResponseEntity.created(URI.create("/api/v1/crew/" + crewId + "/qualifications/" + result.qualificationId())).body(result);
    }

    @PutMapping(value = "/{qualificationId}/expiry", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CrewQualificationView expiry(@PathVariable @Positive Long crewId, @PathVariable @Positive Long qualificationId,
            @Valid @RequestBody ExpiryUpdateRequest request,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        return qualifications.updateExpiry(crewId, qualificationId, new QualificationExpiryRequest(request.validUntil()), ApiPreconditions.require(expectedVersion));
    }

    @DeleteMapping("/{qualificationId}")
    public ResponseEntity<Void> revoke(@PathVariable @Positive Long crewId, @PathVariable @Positive Long qualificationId,
            @RequestHeader(name = ApiPreconditions.HEADER, required = false) String expectedVersion) {
        qualifications.revoke(crewId, qualificationId, ApiPreconditions.require(expectedVersion));
        return ResponseEntity.noContent().build();
    }
}
