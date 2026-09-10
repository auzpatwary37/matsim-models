package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.OsmElementType;

public record OsmStopHint(
        String osmId,
        OsmElementType elementType,
        OsmStopKind kind,
        Coord coord,
        String name,
        String ref,
        String operator,
        String network,
        Set<String> servedModes,
        List<String> parentRelationIds,
        List<String> nearbyLinkIds) {

    public OsmStopHint {
        osmId = Objects.requireNonNull(osmId, "osmId");
        elementType = Objects.requireNonNull(elementType, "elementType");
        kind = Objects.requireNonNull(kind, "kind");
        coord = Objects.requireNonNull(coord, "coord");
        servedModes = Set.copyOf(servedModes);
        parentRelationIds = List.copyOf(parentRelationIds);
        nearbyLinkIds = List.copyOf(nearbyLinkIds);
    }
}
