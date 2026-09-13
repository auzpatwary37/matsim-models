package com.citymodeler.matsim.models.osm.network;

import java.util.List;

/**
 * Resolved lane count for one travel direction, with provenance.
 *
 * @param undeterminedTotal the directional total that could not be split (i.e. {@code lanes=*}
 *     minus {@code lanes:both_ways}), or {@code null} when the count was determined. It is only
 *     set for an {@link LaneConfidence#UNDETERMINED_SPLIT} result.
 */
public record OsmLaneCount(int lanes, String confidence, Double undeterminedTotal, List<String> issueCodes) {
    public OsmLaneCount {
        if (lanes < 1) {
            throw new IllegalArgumentException("lanes must be >= 1");
        }
        issueCodes = List.copyOf(issueCodes);
    }
}
