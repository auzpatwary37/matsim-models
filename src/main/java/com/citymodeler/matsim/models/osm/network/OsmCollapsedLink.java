package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Objects;

/** A merged link produced by collapsing one or more geometry-only OSM segments. */
public record OsmCollapsedLink(
        String linkId,
        String wayId,
        boolean forward,
        String fromOsmNode,
        String toOsmNode,
        List<OsmLinkRef> sourceSegments) {

    public OsmCollapsedLink {
        linkId = Objects.requireNonNull(linkId, "linkId");
        wayId = Objects.requireNonNull(wayId, "wayId");
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
}
