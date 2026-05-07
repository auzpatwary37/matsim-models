package com.citymodeler.matsim.models.population;

import com.citymodeler.matsim.models.api.Attributes;
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
    private final Attributes attributes = new Attributes();

    public Activity(Id<ActivityFacility> facilityId, String type, String endTime, double x) {
        this.type = type;
        this.facilityId = facilityId;
        if (endTime != null && !endTime.isBlank()) {
            this.endTime = parseTime(endTime);
        }
        this.coord = new Coord(x, 0.0);
    }

    public Activity(Id<ActivityFacility> facilityId, String type, String endTime, double x, double y) {
        this.type = type;
        this.facilityId = facilityId;
        if (endTime != null && !endTime.isBlank()) {
            this.endTime = parseTime(endTime);
        }
        this.coord = new Coord(x, y);
    }

    public Activity(String type) {
        this.type = type;
    }

    private static double parseTime(String time) {
        try {
            String[] parts = time.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int seconds = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return hours * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            return 0.0;
        }
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

    public Attributes getAttributes() {
        return attributes;
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
