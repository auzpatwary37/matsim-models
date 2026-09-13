package com.citymodeler.matsim.models.osm.network;

import java.util.List;

public record OsmGeneratedIds(String wayId, int segmentIndex, boolean forward) {
    public static String linkId(String wayId, int segmentIndex, boolean forward) {
        return "osm_way_" + wayId + "_" + segmentIndex + (forward ? "_f" : "_r");
    }

    public static String nodeId(String osmNodeId) {
        return "osm_node_" + osmNodeId;
    }

    /** Deterministic id for a merged link spanning kept OSM nodes {@code fromOsm -> toOsm}. */
    public static String simplifiedLinkId(String wayId, boolean forward, String fromOsm, String toOsm) {
        return "sim_" + wayId + "_" + (forward ? "f" : "r") + "_" + fromOsm + "_" + toOsm;
    }

    /**
     * Collision-safe id for a merged link. {@link #simplifiedLinkId} is canonical only by first source
     * way + endpoints, so two genuinely distinct chains can share a base id (e.g. a way that doubles
     * back produces two different arcs between the same endpoints). Rather than dropping one physical
     * span, disambiguate with a stable hash of the ordered node sequence in canonical order, so both
     * travel directions of one chain derive the same id and distinct chains never collide.
     */
    public static String uniqueSimplifiedLinkId(String baseId, List<String> canonicalNodeIds) {
        return baseId + "_c" + chainDiscriminator(canonicalNodeIds);
    }

    /** Stable 32-bit hex digest of an ordered node-id sequence. */
    static String chainDiscriminator(List<String> canonicalNodeIds) {
        long hash = 1125899906842597L;
        for (String nodeId : canonicalNodeIds) {
            hash = 31 * hash + nodeId.hashCode();
        }
        return Integer.toHexString((int) hash);
    }
}
