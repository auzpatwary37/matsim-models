package com.citymodeler.matsim.models.osm.network;

import java.util.List;

/** Resolved lane count for one travel direction, with provenance. */
public record OsmLaneCount(int lanes, String confidence, Double undeterminedTotal, List<String> issueCodes) {
    public OsmLaneCount {
        if (lanes < 1) {
            throw new IllegalArgumentException("lanes must be >= 1");
        }
        issueCodes = List.copyOf(issueCodes);
    }
}
