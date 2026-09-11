package com.citymodeler.matsim.models.osm.network;

import java.util.Objects;
import java.util.Set;

/** Knobs governing signal-aware simplification. Pure data, no MATSim dependency. */
public record OsmSimplifyOptions(
        double junctionClusterDistanceMeters,
        boolean preserveSharpBends,
        double sharpBendAngleDegrees,
        Set<String> explicitPreserveOsmNodeIds) {

    public OsmSimplifyOptions {
        if (junctionClusterDistanceMeters < 0) {
            throw new IllegalArgumentException("junctionClusterDistanceMeters must be >= 0");
        }
        if (sharpBendAngleDegrees < 0) {
            throw new IllegalArgumentException("sharpBendAngleDegrees must be >= 0");
        }
        explicitPreserveOsmNodeIds = Set.copyOf(Objects.requireNonNull(explicitPreserveOsmNodeIds, "explicitPreserveOsmNodeIds"));
    }

    /** Derive simplification options from an existing build config. */
    public static OsmSimplifyOptions from(OsmNetworkBuildConfig config) {
        return new OsmSimplifyOptions(
                30.0,
                false,
                config.sharpBendAngleDegrees(),
                config.explicitOsmNodeIdsToKeep());
    }

    public static OsmSimplifyOptions defaults() {
        return new OsmSimplifyOptions(30.0, false, 30.0, Set.of());
    }
}
