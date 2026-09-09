package com.citymodeler.matsim.models.osm.network;

import java.util.Objects;

public record OsmLinkRef(String linkId, String osmWayId, int segmentIndex, boolean forward) {
    public OsmLinkRef {
        linkId = Objects.requireNonNull(linkId, "linkId");
        osmWayId = Objects.requireNonNull(osmWayId, "osmWayId");
    }
}
