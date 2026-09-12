package com.citymodeler.matsim.models.osm.network;

import java.util.*;

/**
 * Decides which OSM nodes remain MATSim routing nodes. A node is kept when it carries an intrinsic
 * reason (signal, turn-restriction via, stop, barrier, crossing [config], semantic tag, sharp bend,
 * explicit preserve), when its true undirected degree is not 2, when the two incident atomic segments
 * are not contractible (different attributes or incompatible directions), or when {@code keepAll}.
 *
 * <p>Contraction is deliberately independent of OSM way boundaries: a degree-2 node is dissolved
 * whether it is mid-way or a way split, so equivalent physical roads produce identical topology.
 */
public final class OsmRoutingNodeSelector {

    private OsmRoutingNodeSelector() {
    }

    public static Set<String> select(OsmSegmentGraph graph,
                                     Map<String, OsmNodeClassification> classification,
                                     boolean keepAll) {
        Set<String> keep = new TreeSet<>();
        for (String nodeId : graph.nodeIds()) {
            if (keepAll) {
                keep.add(nodeId);
                continue;
            }
            OsmNodeClassification c = classification.get(nodeId);
            if (c != null && c.keep()) {
                keep.add(nodeId);
                continue;
            }
            if (!contractible(graph, nodeId)) {
                keep.add(nodeId);
            }
        }
        return keep;
    }

    /** True when the node is a safe pass-through: exactly two compatible incident segments. */
    public static boolean contractible(OsmSegmentGraph graph, String nodeId) {
        List<OsmSegmentGraph.Segment> incident = graph.segmentsFrom(nodeId);
        if (incident.size() != 2) {
            return false;
        }
        OsmSegmentGraph.Segment s1 = incident.get(0);
        OsmSegmentGraph.Segment s2 = incident.get(1);
        if (!compatible(s1, s2)) {
            return false;
        }
        String p = graph.other(s1, nodeId);
        String q = graph.other(s2, nodeId);
        if (p.equals(q)) {
            return false; // a self-loop pair
        }
        boolean pToQ = OsmSegmentGraph.allowsTravel(s1, p, nodeId)
                && OsmSegmentGraph.allowsTravel(s2, nodeId, q);
        boolean qToP = OsmSegmentGraph.allowsTravel(s2, q, nodeId)
                && OsmSegmentGraph.allowsTravel(s1, nodeId, p);
        return pToQ || qToP;
    }

    private static boolean compatible(OsmSegmentGraph.Segment a, OsmSegmentGraph.Segment b) {
        return a.modes().equals(b.modes())
                && Double.compare(a.speed(), b.speed()) == 0
                && Double.compare(a.lanes(), b.lanes()) == 0
                && Double.compare(a.capacityPerLane(), b.capacityPerLane()) == 0
                && a.forwardAllowed() == b.forwardAllowed()
                && a.backwardAllowed() == b.backwardAllowed();
    }
}
