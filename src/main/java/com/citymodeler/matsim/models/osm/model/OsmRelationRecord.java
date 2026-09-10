package com.citymodeler.matsim.models.osm.model;

import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.osm.OsmTagSet;

public record OsmRelationRecord(String id, List<OsmRelationMemberRecord> members, OsmTagSet tags) {
    public OsmRelationRecord {
        id = Objects.requireNonNull(id, "id");
        members = List.copyOf(members);
        tags = Objects.requireNonNull(tags, "tags");
    }
}
