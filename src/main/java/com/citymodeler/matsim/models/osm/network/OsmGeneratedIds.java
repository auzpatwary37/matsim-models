package com.citymodeler.matsim.models.osm.network;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
     * span, disambiguate with a strong digest of the ordered node sequence in canonical order, so both
     * travel directions of one chain derive the same id.
     *
     * <p>The digest is a full 256-bit SHA-256, not a 32-bit hash; the caller additionally guarantees
     * that a collision can never discard topology by uniquifying with an occurrence counter.
     */
    public static String uniqueSimplifiedLinkId(String baseId, List<String> canonicalNodeIds) {
        return baseId + "_c" + chainDiscriminator(canonicalNodeIds);
    }

    /** Deterministic SHA-256 hex digest of an ordered node-id sequence. */
    static String chainDiscriminator(List<String> canonicalNodeIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (String nodeId : canonicalNodeIds) {
                sb.append(nodeId).append('\u0000');
            }
            byte[] bytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
