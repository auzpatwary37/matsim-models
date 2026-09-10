package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record OsmIntersectionLaneHint(
        String nodeId,
        List<String> incomingLinkIds,
        List<String> outgoingLinkIds,
        Map<String, Double> approachLanesByIncomingLinkId,
        Map<String, String> turnLanesByIncomingLinkId,
        boolean trafficSignal) {

    public OsmIntersectionLaneHint {
        nodeId = Objects.requireNonNull(nodeId, "nodeId");
        incomingLinkIds = List.copyOf(incomingLinkIds);
        outgoingLinkIds = List.copyOf(outgoingLinkIds);
        approachLanesByIncomingLinkId = Map.copyOf(approachLanesByIncomingLinkId);
        turnLanesByIncomingLinkId = Map.copyOf(turnLanesByIncomingLinkId);
    }
}
