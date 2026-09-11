package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Objects;

/** Classification outcome for a single OSM node referenced by an accepted way. */
public record OsmNodeClassification(String osmNodeId, List<OsmNodeReason> reasons) {
    public OsmNodeClassification {
        osmNodeId = Objects.requireNonNull(osmNodeId, "osmNodeId");
        reasons = List.copyOf(reasons);
    }

    /** True when the node must be kept as a routing node; false when it is geometry-only. */
    public boolean keep() {
        return !reasons.isEmpty();
    }
}
