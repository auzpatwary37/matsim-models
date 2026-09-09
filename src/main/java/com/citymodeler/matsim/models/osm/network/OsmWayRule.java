package com.citymodeler.matsim.models.osm.network;

import java.util.Objects;
import java.util.Set;

public record OsmWayRule(
        String key,
        String value,
        int hierarchy,
        Set<String> allowedModes,
        double lanesPerDirection,
        double freespeedMetersPerSecond,
        double capacityPerLane,
        boolean defaultOneway,
        boolean transitRelevant) {

    public OsmWayRule {
        key = Objects.requireNonNull(key, "key");
        value = Objects.requireNonNull(value, "value");
        allowedModes = Set.copyOf(allowedModes);
        if (lanesPerDirection <= 0.0) throw new IllegalArgumentException("lanesPerDirection must be positive");
        if (freespeedMetersPerSecond <= 0.0) throw new IllegalArgumentException("freespeedMetersPerSecond must be positive");
        if (capacityPerLane <= 0.0) throw new IllegalArgumentException("capacityPerLane must be positive");
    }
}
