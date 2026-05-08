package com.citymodeler.matsim.models.population;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

public final class NetworkRoute implements Route {
    private Id<Link> startLinkId;
    private Id<Link> endLinkId;
    private final List<Id<Link>> linkIds = new ArrayList<>();
    private double travelTime;
    private double distance;

    public Id<Link> getStartLinkId() {
        return startLinkId;
    }

    public void setStartLinkId(Id<Link> startLinkId) {
        this.startLinkId = startLinkId;
    }

    public Id<Link> getEndLinkId() {
        return endLinkId;
    }

    public void setEndLinkId(Id<Link> endLinkId) {
        this.endLinkId = endLinkId;
    }

    public List<Id<Link>> getLinkIds() {
        return Collections.unmodifiableList(linkIds);
    }

    public void addLinkId(Id<Link> linkId) {
        linkIds.add(Objects.requireNonNull(linkId, "linkId"));
    }

    public double getTravelTime() {
        return travelTime;
    }

    public void setTravelTime(double travelTime) {
        this.travelTime = travelTime;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }
}
