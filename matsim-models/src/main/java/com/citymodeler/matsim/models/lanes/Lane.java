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

    public Attributes getAttributes() {
        return attributes;
    }
}
