package com.citymodeler.matsim.models.vehicles;

import java.util.Objects;

import com.citymodeler.matsim.models.api.Id;

public final class VehicleType {
    private final Id<VehicleType> id;
    private double seatingCapacity;
    private double standingCapacity;
    private double lengthMeters;
    private double widthMeters;
    private double accessTimeSeconds;
    private double egressTimeSeconds;

    public VehicleType(Id<VehicleType> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<VehicleType> getId() {
        return id;
    }

    public double getSeatingCapacity() {
        return seatingCapacity;
    }

    public void setSeatingCapacity(double seatingCapacity) {
        requireNonNegative("seatingCapacity", seatingCapacity);
        this.seatingCapacity = seatingCapacity;
    }

    public double getStandingCapacity() {
        return standingCapacity;
    }

    public void setStandingCapacity(double standingCapacity) {
        requireNonNegative("standingCapacity", standingCapacity);
        this.standingCapacity = standingCapacity;
    }

    public double getLengthMeters() {
        return lengthMeters;
    }

    public void setLengthMeters(double lengthMeters) {
        requireNonNegative("lengthMeters", lengthMeters);
        this.lengthMeters = lengthMeters;
    }

    public double getWidthMeters() {
        return widthMeters;
    }

    public void setWidthMeters(double widthMeters) {
        requireNonNegative("widthMeters", widthMeters);
        this.widthMeters = widthMeters;
    }

    public double getAccessTimeSeconds() {
        return accessTimeSeconds;
    }

    public void setAccessTimeSeconds(double accessTimeSeconds) {
        requireNonNegative("accessTimeSeconds", accessTimeSeconds);
        this.accessTimeSeconds = accessTimeSeconds;
    }

    public double getEgressTimeSeconds() {
        return egressTimeSeconds;
    }

    public void setEgressTimeSeconds(double egressTimeSeconds) {
        requireNonNegative("egressTimeSeconds", egressTimeSeconds);
        this.egressTimeSeconds = egressTimeSeconds;
    }

    private static void requireNonNegative(String field, double value) {
        if (value < 0.0) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VehicleType that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
