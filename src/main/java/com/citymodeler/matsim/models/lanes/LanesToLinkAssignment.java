package com.citymodeler.matsim.models.lanes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

public final class LanesToLinkAssignment {
    private final Id<Link> linkId;
    private final Map<Id<Lane>, Lane> lanes = new LinkedHashMap<>();

    public LanesToLinkAssignment(Id<Link> linkId) {
        this.linkId = Objects.requireNonNull(linkId, "linkId");
    }

    public Id<Link> getLinkId() {
        return linkId;
    }

    public Map<Id<Lane>, Lane> getLanes() {
        return Collections.unmodifiableMap(lanes);
    }

    public void addLane(Lane lane) {
        Objects.requireNonNull(lane, "lane");
        lanes.put(lane.getId(), lane);
    }
}
