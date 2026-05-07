package com.citymodeler.matsim.models.facilities;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

public final class ActivityFacility {
    private final Id<ActivityFacility> id;
    private Coord coord;
    private Id<Link> linkId;
    private final Map<String, ActivityOption> activityOptions = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();
    private String desc;

    public ActivityFacility(Id<ActivityFacility> id, Coord coord) {
        this.id = Objects.requireNonNull(id, "id");
        this.coord = Objects.requireNonNull(coord, "coord");
    }

    public Id<ActivityFacility> getId() {
        return id;
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

    public Map<String, ActivityOption> getActivityOptions() {
        return activityOptions;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public void addActivityOption(ActivityOption activityOption) {
        activityOptions.put(activityOption.getType(), activityOption);
    }
}
