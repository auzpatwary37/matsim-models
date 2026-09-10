package com.citymodeler.matsim.models.osm;

import java.util.Objects;

public record OsmElementId(OsmElementType type, String id) {
    public OsmElementId {
        type = Objects.requireNonNull(type, "type");
        id = Objects.requireNonNull(id, "id");
    }
}
