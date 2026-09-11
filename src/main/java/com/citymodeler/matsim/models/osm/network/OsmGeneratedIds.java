package com.citymodeler.matsim.models.osm.network;

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
}
