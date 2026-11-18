package com.aeroroster.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "flight_audit")
@Immutable
public class FlightAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_id", nullable = false, insertable = false, updatable = false)
    private Long flightId;

    @Column(name = "flight_number", nullable = false, length = 15, insertable = false, updatable = false)
    private String flightNumber;

    @Column(name = "action", nullable = false, length = 10, insertable = false, updatable = false)
    private String action;

    @Column(name = "action_time", nullable = false, insertable = false, updatable = false)
    private Instant actionTime;

    protected FlightAudit() {
    }

    public Long getId() {
        return id;
    }

    public Long getFlightId() {
        return flightId;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getAction() {
        return action;
    }

    public Instant getActionTime() {
        return actionTime;
    }
}
