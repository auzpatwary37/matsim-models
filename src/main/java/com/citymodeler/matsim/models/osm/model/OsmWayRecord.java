package com.citymodeler.matsim.models.osm.model;

import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.osm.OsmTagSet;

public record OsmWayRecord(String id, List<String> nodeRefs, OsmTagSet tags) {
    public OsmWayRecord {
        id = Objects.requireNonNull(id, "id");
        nodeRefs = List.copyOf(nodeRefs);
        tags = Objects.requireNonNull(tags, "tags");
    }
}
