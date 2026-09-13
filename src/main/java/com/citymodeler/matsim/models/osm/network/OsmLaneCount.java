package com.citymodeler.matsim.models.osm.network;

import java.util.List;

/**
 * Resolved lane count for one travel direction, with provenance.
 *
 * @param lanes the number of physical lane objects for this direction. For an
 *     {@link LaneConfidence#UNDETERMINED_SPLIT} result this is {@code round(T / 2)} (minimum 1),
 *     never an invented per-direction total.
 * @param confidence provenance vocabulary value (see {@link LaneConfidence}).
 * @param undeterminedTotal the directional total that could not be split (i.e. {@code lanes=*}
 *     minus {@code lanes:both_ways}), or {@code null} when the count was determined. It is only
 *     set for an {@link LaneConfidence#UNDETERMINED_SPLIT} result.
 * @param bothWays the {@code lanes:both_ways} count when present and valid (provenance only; it is
 *     never added to {@link #lanes}), or {@code null} when the tag is absent or malformed.
 */
public record OsmLaneCount(int lanes, String confidence, Double undeterminedTotal, Double bothWays,
        List<String> issueCodes) {
    public OsmLaneCount {
        if (lanes < 1) {
            throw new IllegalArgumentException("lanes must be >= 1");
        }
        issueCodes = List.copyOf(issueCodes);
    }

    /** Backward-compatible 4-argument form without a {@code lanes:both_ways} value. */
    public OsmLaneCount(int lanes, String confidence, Double undeterminedTotal, List<String> issueCodes) {
        this(lanes, confidence, undeterminedTotal, null, issueCodes);
    }
}
