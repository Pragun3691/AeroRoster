package com.aeroroster.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "aircraft_type")
public class AircraftType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model", nullable = false, length = 60)
    private String model;

    @Column(name = "manufacturer", nullable = false, length = 80)
    private String manufacturer;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "min_cabin_crew", nullable = false)
    private int minCabinCrew;

    public AircraftType() {
    }

    public Long getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getMinCabinCrew() {
        return minCabinCrew;
    }

    public void setMinCabinCrew(int minCabinCrew) {
        this.minCabinCrew = minCabinCrew;
    }
}
