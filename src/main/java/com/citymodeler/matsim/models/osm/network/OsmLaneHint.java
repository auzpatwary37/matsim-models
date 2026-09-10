package com.citymodeler.matsim.models.osm.network;

import java.util.Objects;
import java.util.Set;

public record OsmLaneHint(
        String linkId,
        String osmWayId,
        boolean forward,
        double totalLanes,
        double busLanes,
        double psvLanes,
        String turnLanes,
        boolean dedicatedTransitLane,
        Set<String> servedModes) {

    public OsmLaneHint {
        linkId = Objects.requireNonNull(linkId, "linkId");
        osmWayId = Objects.requireNonNull(osmWayId, "osmWayId");
        servedModes = Set.copyOf(servedModes);
    }
}
