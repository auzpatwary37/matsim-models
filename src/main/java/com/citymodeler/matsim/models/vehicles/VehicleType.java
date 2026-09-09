package com.citymodeler.matsim.models.vehicles;

import java.util.Objects;

import com.citymodeler.matsim.models.api.Id;

/**
 * Vehicle type following the current MATSim vehicleDefinitions v2.0 wire
 * contract: seating and standing capacities are non-negative INTEGER counts
 * (the v2.0 schema declares them as {@code xs:nonNegativeInteger}), while
 * dimensions and per-person access/egress times are non-negative finite
 * doubles.
 */
public final class VehicleType {
    private final Id<VehicleType> id;
    private Integer seatingCapacity;
    private Integer standingCapacity;
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

    public Integer getSeatingCapacity() {
        return seatingCapacity;
    }

    public void setSeatingCapacity(int seatingCapacity) {
        if (seatingCapacity < 0) {
            throw new IllegalArgumentException("seatingCapacity must not be negative: " + seatingCapacity);
        }
        this.seatingCapacity = seatingCapacity;
    }

    public Integer getStandingCapacity() {
        return standingCapacity;
    }

    public void setStandingCapacity(int standingCapacity) {
        if (standingCapacity < 0) {
            throw new IllegalArgumentException("standingCapacity must not be negative: " + standingCapacity);
        }
        this.standingCapacity = standingCapacity;
    }

    public double getLengthMeters() {
        return lengthMeters;
    }

    public void setLengthMeters(double lengthMeters) {
        requireFiniteNonNegative("lengthMeters", lengthMeters);
        this.lengthMeters = lengthMeters;
    }

    public double getWidthMeters() {
        return widthMeters;
    }

    public void setWidthMeters(double widthMeters) {
        requireFiniteNonNegative("widthMeters", widthMeters);
        this.widthMeters = widthMeters;
    }

    public double getAccessTimeSeconds() {
        return accessTimeSeconds;
    }

    public void setAccessTimeSeconds(double accessTimeSeconds) {
        requireFiniteNonNegative("accessTimeSeconds", accessTimeSeconds);
        this.accessTimeSeconds = accessTimeSeconds;
    }

    public double getEgressTimeSeconds() {
        return egressTimeSeconds;
    }

    public void setEgressTimeSeconds(double egressTimeSeconds) {
        requireFiniteNonNegative("egressTimeSeconds", egressTimeSeconds);
        this.egressTimeSeconds = egressTimeSeconds;
    }

    private static void requireFiniteNonNegative(String field, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite: " + value);
        }
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
