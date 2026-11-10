package com.citymodeler.matsim.models.lanes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

public final class Lane {
    private final Id<Lane> id;
    private final List<Id<Link>> toLinkIds = new ArrayList<>();
    private final List<Id<Lane>> toLaneIds = new ArrayList<>();
    private double capacityVehiclesPerHour;
    private double startsAtMeterFromLinkEnd;
    private String alignment;
    private Double representedLanesNumber;
    private Double lengthMeter;
    private final Attributes attributes = new Attributes();

    public Lane(Id<Lane> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<Lane> getId() {
        return id;
    }

    public List<Id<Link>> getToLinkIds() {
        return Collections.unmodifiableList(toLinkIds);
    }

    public List<Id<Lane>> getToLaneIds() {
        return Collections.unmodifiableList(toLaneIds);
    }

    public void addToLinkId(Id<Link> linkId) {
        toLinkIds.add(linkId);
    }

    public void addToLaneId(Id<Lane> laneId) {
        toLaneIds.add(laneId);
    }

    public double getCapacityVehiclesPerHour() {
        return capacityVehiclesPerHour;
    }

    public void setCapacityVehiclesPerHour(double capacityVehiclesPerHour) {
        this.capacityVehiclesPerHour = capacityVehiclesPerHour;
    }

    public double getStartsAtMeterFromLinkEnd() {
        return startsAtMeterFromLinkEnd;
    }

    public void setStartsAtMeterFromLinkEnd(double startsAtMeterFromLinkEnd) {
        this.startsAtMeterFromLinkEnd = startsAtMeterFromLinkEnd;
    }

    public String getAlignment() {
        return alignment;
    }

    public void setAlignment(String alignment) {
        this.alignment = alignment;
    }

    /**
     * Canonical {@code representedLanes/@number}: how many physical lanes this
     * definition represents. {@code null} means absent (schema default 1).
     */
    public Double getRepresentedLanesNumber() {
        return representedLanesNumber;
    }

    public void setRepresentedLanesNumber(Double representedLanesNumber) {
        this.representedLanesNumber = representedLanesNumber;
    }

    /**
     * Lane geometry length in meters as carried by the laneDefinitions_v1.1
     * {@code length/@meter} element. The v2.0 schema has no lane length element
     * (v2 positions lanes with {@code startsAt}), so a v1.1-sourced model keeps
     * the value here and the v2 writer round-trips it through the
     * {@code osm:lane.lengthMeter} provenance attribute. {@code null} means
     * absent (v1.1 schema default 45.0).
     */
    public Double getLengthMeter() {
        return lengthMeter;
    }

    public void setLengthMeter(Double lengthMeter) {
        this.lengthMeter = lengthMeter;
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
