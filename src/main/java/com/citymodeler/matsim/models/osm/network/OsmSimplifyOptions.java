package com.citymodeler.matsim.models.osm.network;

import java.util.Objects;
import java.util.Set;

/**
 * Knobs governing signal-aware simplification. Pure data, no MATSim dependency.
 *
 * <p>{@code junctionClusterDistanceMeters} defaults to {@code 0}, i.e. no distance-based
 * clustering: each signalized node is its own junction. This is the safe default because
 * merging two distinct signalized intersections is worse than leaving a complex intersection
 * split. When an operator opts in with a positive threshold, clustering is topology-gated:
 * two signalized nodes only cluster if a plausible short internal path connects them
 * (see {@code maxClusterHops}) and the path never transits another signalized node.
 */
public record OsmSimplifyOptions(
        double junctionClusterDistanceMeters,
        boolean preserveSharpBends,
        double sharpBendAngleDegrees,
        Set<String> explicitPreserveOsmNodeIds,
        int maxClusterHops) {

    public OsmSimplifyOptions {
        if (junctionClusterDistanceMeters < 0) {
            throw new IllegalArgumentException("junctionClusterDistanceMeters must be >= 0");
        }
        if (sharpBendAngleDegrees < 0) {
            throw new IllegalArgumentException("sharpBendAngleDegrees must be >= 0");
        }
        if (maxClusterHops < 1) {
            throw new IllegalArgumentException("maxClusterHops must be >= 1");
        }
        explicitPreserveOsmNodeIds = Set.copyOf(Objects.requireNonNull(explicitPreserveOsmNodeIds, "explicitPreserveOsmNodeIds"));
    }

    /** Convenience 4-arg form using the default hop budget. */
    public OsmSimplifyOptions(double junctionClusterDistanceMeters,
                              boolean preserveSharpBends,
                              double sharpBendAngleDegrees,
                              Set<String> explicitPreserveOsmNodeIds) {
        this(junctionClusterDistanceMeters, preserveSharpBends, sharpBendAngleDegrees,
                explicitPreserveOsmNodeIds, DEFAULT_MAX_CLUSTER_HOPS);
    }

    /** Derive simplification options from an existing build config. */
    public static OsmSimplifyOptions from(OsmNetworkBuildConfig config) {
        return new OsmSimplifyOptions(
                0.0,
                false,
                config.sharpBendAngleDegrees(),
                config.explicitOsmNodeIdsToKeep(),
                DEFAULT_MAX_CLUSTER_HOPS);
    }

    public static OsmSimplifyOptions defaults() {
        return new OsmSimplifyOptions(0.0, false, 30.0, Set.of(), DEFAULT_MAX_CLUSTER_HOPS);
    }

    static final int DEFAULT_MAX_CLUSTER_HOPS = 4;
}
