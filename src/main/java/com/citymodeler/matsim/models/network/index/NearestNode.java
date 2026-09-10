package com.citymodeler.matsim.models.network.index;

import java.util.Objects;

import com.citymodeler.matsim.models.api.Coord;

public record NearestNode(
        String nodeId,
        double distance,
        Coord nodeCoord) {

    public NearestNode {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(nodeCoord, "nodeCoord");
    }
}
