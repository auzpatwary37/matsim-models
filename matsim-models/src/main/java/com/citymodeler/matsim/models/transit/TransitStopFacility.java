package com.citymodeler.matsim.models.transit;

import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

public final class TransitStopFacility {
    private Id<TransitStopFacility> id;
    private Coord coord;
    private Id<Link> linkId;
    private String name;
    private boolean blockingLane;
    private final Attributes attributes = new Attributes();

    public TransitStopFacility(Id<TransitStopFacility> id, Coord coord, boolean blockingLane) {
        this.id = Objects.requireNonNull(id, "id");
        this.coord = Objects.requireNonNull(coord, "coord");
        this.blockingLane = blockingLane;
    }

    public Id<TransitStopFacility> getId() {
        return id;
    }

    public void setId(Id<TransitStopFacility> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Coord getCoord() {
        return coord;
    }

    public void setCoord(Coord coord) {
        this.coord = Objects.requireNonNull(coord, "coord");
    }

    public Id<Link> getLinkId() {
        return linkId;
    }

    public void setLinkId(Id<Link> linkId) {
        this.linkId = linkId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isBlockingLane() {
        return blockingLane;
    }

    public void setBlockingLane(boolean blockingLane) {
        this.blockingLane = blockingLane;
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
