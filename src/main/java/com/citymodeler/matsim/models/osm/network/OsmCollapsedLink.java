package com.citymodeler.matsim.models.osm.network;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** A merged MATSim link produced by contracting one or more atomic OSM segments, possibly spanning multiple OSM ways. */
public record OsmCollapsedLink(
        String linkId,
        boolean forward,
        String fromOsmNode,
        String toOsmNode,
        List<OsmLinkRef> sourceSegments) {

    public OsmCollapsedLink {
        linkId = Objects.requireNonNull(linkId, "linkId");
        fromOsmNode = Objects.requireNonNull(fromOsmNode, "fromOsmNode");
        toOsmNode = Objects.requireNonNull(toOsmNode, "toOsmNode");
        sourceSegments = List.copyOf(Objects.requireNonNull(sourceSegments, "sourceSegments"));
        if (sourceSegments.isEmpty()) {
            throw new IllegalArgumentException("sourceSegments must not be empty");
        }
    }

    /** Number of original materialized segments folded into this link. */
    public int segmentCount() {
        return sourceSegments.size();
    }

    /** Distinct source OSM way ids in segment order (a merged link may span several ways). */
    public List<String> sourceOsmWayIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (OsmLinkRef r : sourceSegments) {
            ids.add(r.osmWayId());
        }
        return List.copyOf(ids);
    }

    /** The way id of the first source segment; used for deterministic link-id derivation. */
    public String firstOsmWayId() {
        return sourceSegments.get(0).osmWayId();
    }
}
