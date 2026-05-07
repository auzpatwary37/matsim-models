package com.citymodeler.matsim.models.transit;

import java.util.Objects;

import com.citymodeler.matsim.models.api.Id;

public final class Departure {
    private Id<Departure> id;
    private double departureTime;
    private String vehicleId;

    public Departure(Id<Departure> id, double departureTime) {
        this.id = Objects.requireNonNull(id, "id");
        this.departureTime = departureTime;
    }

    public Id<Departure> getId() {
        return id;
    }

    public void setId(Id<Departure> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public double getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(double departureTime) {
        this.departureTime = departureTime;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }
}
