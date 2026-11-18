package com.aeroroster.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "duty_assignment")
public class DutyAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    private Crew crew;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", nullable = false, length = 20)
    private CrewRole assignedRole;

    @Column(name = "duty_start_time", nullable = false)
    private Instant dutyStartTime;

    @Column(name = "duty_end_time", nullable = false)
    private Instant dutyEndTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    public DutyAssignment() {
    }

    public Long getId() {
        return id;
    }

    public Crew getCrew() {
        return crew;
    }

    public void setCrew(Crew crew) {
        this.crew = crew;
    }

    public Flight getFlight() {
        return flight;
    }

    public void setFlight(Flight flight) {
        this.flight = flight;
    }

    public CrewRole getAssignedRole() {
        return assignedRole;
    }

    public void setAssignedRole(CrewRole assignedRole) {
        this.assignedRole = assignedRole;
    }

    public Instant getDutyStartTime() {
        return dutyStartTime;
    }

    public void setDutyStartTime(Instant dutyStartTime) {
        this.dutyStartTime = dutyStartTime;
    }

    public Instant getDutyEndTime() {
        return dutyEndTime;
    }

    public void setDutyEndTime(Instant dutyEndTime) {
        this.dutyEndTime = dutyEndTime;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
