package com.citymodeler.matsim.models.population;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.network.Link;

public final class Activity implements PlanElement {
    private String type;
    private Coord coord;
    private Id<Link> linkId;
    private Id<ActivityFacility> facilityId;
    private double startTime = Double.MAX_VALUE;
    private double endTime = Double.MAX_VALUE;
    private double maximumDuration = Double.MAX_VALUE;

    public Activity(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Coord getCoord() {
        return coord;
    }

    public void setCoord(Coord coord) {
        this.coord = coord;
    }

    public Id<Link> getLinkId() {
        return linkId;
    }

    public void setLinkId(Id<Link> linkId) {
        this.linkId = linkId;
    }

    public Id<ActivityFacility> getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(Id<ActivityFacility> facilityId) {
        this.facilityId = facilityId;
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getEndTime() {
        return endTime;
    }

    public void setEndTime(double endTime) {
        this.endTime = endTime;
    }

    public double getMaximumDuration() {
        return maximumDuration;
    }

    public void setMaximumDuration(double maximumDuration) {
        this.maximumDuration = maximumDuration;
    }
}
