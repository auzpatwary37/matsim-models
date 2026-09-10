package com.citymodeler.matsim.models.osm.model;

import java.util.Objects;

import com.citymodeler.matsim.models.osm.OsmElementType;

public record OsmRelationMemberRecord(OsmElementType type, String ref, String role) {
    public OsmRelationMemberRecord {
        type = Objects.requireNonNull(type, "type");
        ref = Objects.requireNonNull(ref, "ref");
        role = role == null ? "" : role;
    }
}
