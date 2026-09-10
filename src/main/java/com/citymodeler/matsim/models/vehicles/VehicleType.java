package com.citymodeler.matsim.models.vehicles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

/**
 * Vehicle type following the current MATSim vehicleDefinitions v2.0 wire
 * contract: seating and standing capacities are non-negative INTEGER counts
 * (the v2.0 schema declares them as {@code xs:nonNegativeInteger}), while
 * dimensions and per-person access/egress times are non-negative finite
 * doubles.
 *
 * <p>Access/egress times are nullable to distinguish "unset" from explicit
 * zero. Current MATSim's {@code VehicleUtils.getAccessTime()} returns
 * 1.0 s/person when the attribute is absent; {@link #getEffectiveAccessTimeSeconds()}
 * and {@link #getEffectiveEgressTimeSeconds()} expose that effective default.</p>
 */
public final class VehicleType {
    public static final double DEFAULT_ACCESS_TIME_SECONDS = 1.0;
    public static final double DEFAULT_EGRESS_TIME_SECONDS = 1.0;

    private final Id<VehicleType> id;
    private Integer seatingCapacity;
    private Integer standingCapacity;
    private double lengthMeters;
    private double widthMeters;
    private Double accessTimeSeconds;
    private Double egressTimeSeconds;
    private final Attributes extraAttributes = new Attributes();
    private final List<ExtensionElement> extensionElements = new ArrayList<>();
    private String capacityVolumeInCubicMeters;
    private String capacityWeightInTons;
    private String capacityOther;
    private final Attributes capacityExtraAttributes = new Attributes();

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

    public Double getAccessTimeSeconds() {
        return accessTimeSeconds;
    }

    public void setAccessTimeSeconds(double accessTimeSeconds) {
        requireFiniteNonNegative("accessTimeSeconds", accessTimeSeconds);
        this.accessTimeSeconds = accessTimeSeconds;
    }

    public void clearAccessTimeSeconds() {
        this.accessTimeSeconds = null;
    }

    public Double getEgressTimeSeconds() {
        return egressTimeSeconds;
    }

    public void setEgressTimeSeconds(double egressTimeSeconds) {
        requireFiniteNonNegative("egressTimeSeconds", egressTimeSeconds);
        this.egressTimeSeconds = egressTimeSeconds;
    }

    public void clearEgressTimeSeconds() {
        this.egressTimeSeconds = null;
    }

    public double getEffectiveAccessTimeSeconds() {
        return accessTimeSeconds != null ? accessTimeSeconds : DEFAULT_ACCESS_TIME_SECONDS;
    }

    public double getEffectiveEgressTimeSeconds() {
        return egressTimeSeconds != null ? egressTimeSeconds : DEFAULT_EGRESS_TIME_SECONDS;
    }

    public Attributes getExtraAttributes() {
        return extraAttributes;
    }

    public void addExtensionElement(String tagName, String serializedXml) {
        extensionElements.add(new ExtensionElement(tagName, serializedXml));
    }

    public List<ExtensionElement> getExtensionElements() {
        return Collections.unmodifiableList(extensionElements);
    }

    public String getCapacityVolumeInCubicMeters() {
        return capacityVolumeInCubicMeters;
    }

    public void setCapacityVolumeInCubicMeters(String value) {
        this.capacityVolumeInCubicMeters = value;
    }

    public String getCapacityWeightInTons() {
        return capacityWeightInTons;
    }

    public void setCapacityWeightInTons(String value) {
        this.capacityWeightInTons = value;
    }

    public String getCapacityOther() {
        return capacityOther;
    }

    public void setCapacityOther(String value) {
        this.capacityOther = value;
    }

    public Attributes getCapacityExtraAttributes() {
        return capacityExtraAttributes;
    }

    public record ExtensionElement(String tagName, String serializedXml) {
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
