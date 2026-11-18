package com.aeroroster.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "qualification")
public class Qualification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_type_id", nullable = false)
    private AircraftType aircraftType;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_role", nullable = false, length = 20)
    private CrewRole requiredRole;

    public Qualification() {
    }

    public Long getId() {
        return id;
    }

    public AircraftType getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(AircraftType aircraftType) {
        this.aircraftType = aircraftType;
    }

    public CrewRole getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(CrewRole requiredRole) {
        this.requiredRole = requiredRole;
    }
}
