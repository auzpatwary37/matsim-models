package com.citymodeler.matsim.models.osm.model;

import java.util.Objects;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.OsmTagSet;

public record OsmNodeRecord(String id, double lon, double lat, Coord projectedCoord, OsmTagSet tags) {
    public OsmNodeRecord {
        id = Objects.requireNonNull(id, "id");
        projectedCoord = Objects.requireNonNull(projectedCoord, "projectedCoord");
        tags = Objects.requireNonNull(tags, "tags");
    }
}
