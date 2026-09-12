package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Classifies each OSM node referenced by an accepted way as either kept (a routing node carrying
 * routing, lane, signal, access, transit, or provenance significance) or geometry-only.
 *
 * <p>The classification is deterministic: input collections are traversed in sorted order and all
 * internal accumulators are backed by {@link TreeMap}/{@link TreeSet}.
 */
public final class OsmNodeClassifier {

    private OsmNodeClassifier() {
    }

    public static Map<String, OsmNodeClassification> classify(
            OsmImportResult importResult,
            Set<String> acceptedWayIds,
            Set<String> transitStopNodes,
            Set<String> restrictionViaNodes,
            boolean preserveSharpBends,
            double sharpBendAngleDegrees,
            Set<String> explicitPreserveNodes) {

        Map<String, Set<String>> nodeWays = new TreeMap<>();
        Set<String> endpoints = new TreeSet<>();
        Map<String, OsmWayRecord> accepted = new TreeMap<>();

        for (String wayId : new TreeSet<>(acceptedWayIds)) {
            OsmWayRecord w = importResult.ways().get(wayId);
            if (w == null || w.nodeRefs().size() < 2) {
                continue;
            }
            accepted.put(wayId, w);
            List<String> nr = w.nodeRefs();
            endpoints.add(nr.get(0));
            endpoints.add(nr.get(nr.size() - 1));
            for (String n : new LinkedHashSet<>(nr)) {
                nodeWays.computeIfAbsent(n, k -> new TreeSet<>()).add(wayId);
            }
        }

        Set<String> sharpBends = new TreeSet<>();
        if (preserveSharpBends) {
            double threshold = 180.0 - sharpBendAngleDegrees;
            for (OsmWayRecord w : accepted.values()) {
                List<String> nr = w.nodeRefs();
                for (int i = 1; i + 1 < nr.size(); i++) {
                    OsmNodeRecord a = importResult.nodes().get(nr.get(i - 1));
                    OsmNodeRecord b = importResult.nodes().get(nr.get(i));
                    OsmNodeRecord c = importResult.nodes().get(nr.get(i + 1));
                    if (a == null || b == null || c == null) {
                        continue;
                    }
                    if (angleAtB(a, b, c) < threshold) {
                        sharpBends.add(nr.get(i));
                    }
                }
            }
        }

        Map<String, OsmNodeClassification> out = new TreeMap<>();
        for (String nodeId : new TreeSet<>(nodeWays.keySet())) {
            List<OsmNodeReason> reasons = new ArrayList<>();
            if (endpoints.contains(nodeId)) {
                reasons.add(OsmNodeReason.WAY_ENDPOINT);
            }
            if (nodeWays.get(nodeId).size() >= 2) {
                reasons.add(OsmNodeReason.SHARED_BY_MULTIPLE_WAYS);
            }
            OsmNodeRecord rec = importResult.nodes().get(nodeId);
            if (rec != null && isSignalized(rec.tags())) {
                reasons.add(OsmNodeReason.SIGNALIZED);
            }
            if (restrictionViaNodes.contains(nodeId)) {
                reasons.add(OsmNodeReason.TURN_RESTRICTION_VIA);
            }
            if (transitStopNodes.contains(nodeId)) {
                reasons.add(OsmNodeReason.TRANSIT_STOP);
            }
            if (rec != null && hasSemanticNodeTag(rec.tags())) {
                reasons.add(OsmNodeReason.SEMANTIC_NODE_TAG);
            }
            if (sharpBends.contains(nodeId)) {
                reasons.add(OsmNodeReason.SHARP_BEND);
            }
            if (explicitPreserveNodes.contains(nodeId)) {
                reasons.add(OsmNodeReason.EXPLICIT_PRESERVE);
            }
            out.put(nodeId, new OsmNodeClassification(nodeId, reasons));
        }
        return out;
    }

    /**
     * Intrinsic-only classification: reasons that are true of the node itself, independent of
     * structural degree. Used by OsmRoutingNodeSelector; the legacy classify(...) is retained until
     * Task 5 rewires the signal-aware simplifier.
     */
    public static Map<String, OsmNodeClassification> classifyIntrinsic(
            OsmImportResult importResult,
            Set<String> acceptedWayIds,
            Set<String> transitStopNodes,
            Set<String> restrictionViaNodes,
            boolean preserveSharpBends,
            double sharpBendAngleDegrees,
            Set<String> explicitPreserveNodes,
            boolean preserveCrossingNodes) {

        Map<String, Set<String>> nodeWays = new TreeMap<>();
        Map<String, OsmWayRecord> accepted = new TreeMap<>();

        for (String wayId : new TreeSet<>(acceptedWayIds)) {
            OsmWayRecord w = importResult.ways().get(wayId);
            if (w == null || w.nodeRefs().size() < 2) {
                continue;
            }
            accepted.put(wayId, w);
            for (String n : new LinkedHashSet<>(w.nodeRefs())) {
                nodeWays.computeIfAbsent(n, k -> new TreeSet<>()).add(wayId);
            }
        }

        Set<String> sharpBends = new TreeSet<>();
        if (preserveSharpBends) {
            double threshold = 180.0 - sharpBendAngleDegrees;
            for (OsmWayRecord w : accepted.values()) {
                List<String> nr = w.nodeRefs();
                for (int i = 1; i + 1 < nr.size(); i++) {
                    OsmNodeRecord a = importResult.nodes().get(nr.get(i - 1));
                    OsmNodeRecord b = importResult.nodes().get(nr.get(i));
                    OsmNodeRecord c = importResult.nodes().get(nr.get(i + 1));
                    if (a == null || b == null || c == null) {
                        continue;
                    }
                    if (angleAtB(a, b, c) < threshold) {
                        sharpBends.add(nr.get(i));
                    }
                }
            }
        }

        Map<String, OsmNodeClassification> out = new TreeMap<>();
        for (String nodeId : new TreeSet<>(nodeWays.keySet())) {
            List<OsmNodeReason> reasons = new ArrayList<>();
            Set<OsmNodeReason> seen = new TreeSet<>();
            OsmNodeRecord rec = importResult.nodes().get(nodeId);
            if (rec != null && isSignalized(rec.tags())) {
                addReason(reasons, seen, OsmNodeReason.SIGNALIZED);
            }
            if (restrictionViaNodes.contains(nodeId)) {
                addReason(reasons, seen, OsmNodeReason.TURN_RESTRICTION_VIA);
            }
            if (transitStopNodes.contains(nodeId)) {
                addReason(reasons, seen, OsmNodeReason.TRANSIT_STOP);
            }
            if (rec != null && isBarrier(rec.tags())) {
                addReason(reasons, seen, OsmNodeReason.BARRIER);
            }
            if (preserveCrossingNodes && rec != null && rec.tags().get("crossing") != null) {
                addReason(reasons, seen, OsmNodeReason.CROSSING);
            }
            if (rec != null && hasIntrinsicSemanticTag(rec.tags())) {
                addReason(reasons, seen, OsmNodeReason.SEMANTIC_NODE_TAG);
            }
            if (sharpBends.contains(nodeId)) {
                addReason(reasons, seen, OsmNodeReason.SHARP_BEND);
            }
            if (explicitPreserveNodes.contains(nodeId)) {
                addReason(reasons, seen, OsmNodeReason.EXPLICIT_PRESERVE);
            }
            out.put(nodeId, new OsmNodeClassification(nodeId, reasons));
        }
        return out;
    }

    private static void addReason(List<OsmNodeReason> reasons, Set<OsmNodeReason> seen, OsmNodeReason reason) {
        if (seen.add(reason)) {
            reasons.add(reason);
        }
    }

    /** True when the node is explicitly tagged as a traffic signal. */
    static boolean isSignalized(OsmTagSet t) {
        if (t.has("highway", "traffic_signals")) {
            return true;
        }
        String ts = t.get("traffic_signals");
        return ts != null && !"no".equals(ts) && !"none".equals(ts) && !"0".equals(ts);
    }

    /** True when the node carries a control / stop / barrier / crossing / transit node tag. */
    static boolean hasSemanticNodeTag(OsmTagSet t) {
        if (t.has("highway", "stop") || t.has("highway", "give_way")) {
            return true;
        }
        if (t.has("highway", "bus_stop") || t.has("highway", "tram_stop")) {
            return true;
        }
        if (t.get("public_transport") != null || t.get("railway") != null) {
            return true;
        }
        if (t.get("barrier") != null || t.get("bollard") != null) {
            return true;
        }
        return t.get("crossing") != null;
    }

    /** True when the node carries a control / stop / barrier / transit node tag (excluding crossing). */
    static boolean hasIntrinsicSemanticTag(OsmTagSet t) {
        if (t.has("highway", "stop") || t.has("highway", "give_way")) {
            return true;
        }
        if (t.has("highway", "bus_stop") || t.has("highway", "tram_stop")) {
            return true;
        }
        if (t.get("public_transport") != null || t.get("railway") != null) {
            return true;
        }
        return t.get("barrier") != null || t.get("bollard") != null;
    }

    /** True when the node is tagged as a physical barrier. */
    static boolean isBarrier(OsmTagSet t) {
        return t.get("barrier") != null || t.get("bollard") != null;
    }

    /** Interior angle (degrees, 0..180; 180 == straight) at b of the path a-b-c. */
    private static double angleAtB(OsmNodeRecord a, OsmNodeRecord b, OsmNodeRecord c) {
        double abx = a.projectedCoord().getX() - b.projectedCoord().getX();
        double aby = a.projectedCoord().getY() - b.projectedCoord().getY();
        double bcx = c.projectedCoord().getX() - b.projectedCoord().getX();
        double bcy = c.projectedCoord().getY() - b.projectedCoord().getY();
        double dot = abx * bcx + aby * bcy;
        double cross = abx * bcy - aby * bcx;
        return Math.toDegrees(Math.atan2(Math.abs(cross), dot));
    }
}
