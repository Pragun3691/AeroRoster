package com.aeroroster.web;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Explicit browser-history routes only; API, documentation and static assets retain their own handlers/404s. */
@Controller
public class SpaHistoryController {
    @GetMapping(value = {"/dashboard", "/crew", "/crew/{crewId:[0-9]+}", "/aircraft", "/aircraft/{aircraftId:[0-9]+}",
            "/flights", "/flights/{flightId:[0-9]+}", "/flights/{flightId:[0-9]+}/assign", "/qualifications", "/qualifications/{qualificationId:[0-9]+}",
            "/assignments", "/assignments/{assignmentId:[0-9]+}", "/unavailability", "/unavailability/{unavailabilityId:[0-9]+}"},
            produces = MediaType.TEXT_HTML_VALUE)
    public String history() {
        return "forward:/index.html";
    }
}
