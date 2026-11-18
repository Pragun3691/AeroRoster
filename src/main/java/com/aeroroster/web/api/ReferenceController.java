package com.aeroroster.web.api;

import com.aeroroster.service.AirportService;
import com.aeroroster.service.AircraftTypeService;
import com.aeroroster.service.QualificationService;
import com.aeroroster.service.dto.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReferenceController {
    private final AirportService airports;
    private final AircraftTypeService types;
    private final QualificationService qualifications;

    public ReferenceController(AirportService airports, AircraftTypeService types, QualificationService qualifications) {
        this.airports = airports;
        this.types = types;
        this.qualifications = qualifications;
    }

    @GetMapping("/airports")
    public List<AirportView> airports(@RequestParam(required = false) @Size(max = 200) String q) {
        return airports.search(q);
    }

    @GetMapping("/airports/{airportId}")
    public AirportView airport(@PathVariable @Positive Long airportId) {
        return airports.get(airportId);
    }

    @GetMapping("/aircraft-types")
    public List<AircraftTypeView> aircraftTypes() {
        return types.list();
    }

    @GetMapping("/aircraft-types/{aircraftTypeId}")
    public AircraftTypeView aircraftType(@PathVariable @Positive Long aircraftTypeId) {
        return types.get(aircraftTypeId);
    }

    @GetMapping("/aircraft-types/{aircraftTypeId}/qualifications")
    public List<QualificationView> requirementsForType(@PathVariable @Positive Long aircraftTypeId) {
        return qualifications.requirementsForAircraftType(aircraftTypeId);
    }

    @GetMapping("/qualifications")
    public List<QualificationView> requirements() {
        return qualifications.requirements();
    }
}
