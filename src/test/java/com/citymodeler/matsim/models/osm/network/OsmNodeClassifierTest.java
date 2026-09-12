package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

final class OsmNodeClassifierTest {

    private static OsmNodeRecord node(String id, double x, double y, Map<String, String> tags) {
        return new OsmNodeRecord(id, 0, 0, new Coord(x, y), OsmTagSet.of(tags));
    }

    private static OsmWayRecord way(String id, List<String> nodes, Map<String, String> tags) {
        return new OsmWayRecord(id, nodes, OsmTagSet.of(tags));
    }

    private static OsmImportResult res(Map<String, OsmNodeRecord> n, Map<String, OsmWayRecord> w) {
        return new OsmImportResult(n, w, Map.of(), List.of(),
                OsmProvenance.defaultFor("t.osm", "EPSG:3857"));
    }

    private static Map<String, OsmNodeClassification> classify(OsmImportResult res,
                                                              Set<String> stopNodes,
                                                              Set<String> viaNodes,
                                                              Set<String> explicit,
                                                              boolean preserveCrossing) {
        return OsmNodeClassifier.classifyIntrinsic(
                res, res.ways().keySet(), stopNodes, viaNodes, false, 30.0, explicit, preserveCrossing);
    }

    @Test
    void intrinsicReasonsAreClassifiedAndPlainInteriorCollapses() {
        Map<String, OsmNodeRecord> nodes = Map.of(
                "W", node("W", 0, 100, Map.of()),
                "Wm", node("Wm", 50, 100, Map.of()),
                "N", node("N", 100, 100, Map.of("highway", "traffic_signals")),
                "B", node("B", 150, 100, Map.of("barrier", "bollard")),
                "E", node("E", 200, 100, Map.of()));
        Map<String, OsmWayRecord> ways = Map.of(
                "10", way("10", List.of("W", "Wm", "N", "B", "E"), Map.of("highway", "residential")));
        OsmImportResult res = res(nodes, ways);

        Map<String, OsmNodeClassification> out = classify(res, Set.of("Wm"), Set.of("B"), Set.of("E"), false);

        assertTrue(out.get("N").reasons().contains(OsmNodeReason.SIGNALIZED));
        assertTrue(out.get("N").keep());
        assertTrue(out.get("Wm").reasons().contains(OsmNodeReason.TRANSIT_STOP));
        assertTrue(out.get("B").reasons().contains(OsmNodeReason.TURN_RESTRICTION_VIA));
        assertTrue(out.get("B").reasons().contains(OsmNodeReason.BARRIER));
        assertTrue(out.get("E").reasons().contains(OsmNodeReason.EXPLICIT_PRESERVE));

        // A plain interior node has no reasons and must not be kept.
        assertTrue(out.get("W").reasons().isEmpty());
        assertFalse(out.get("W").keep());
    }

    @Test
    void plainTrafficSignalTagAlsoMarksSignalized() {
        Map<String, OsmNodeRecord> nodes = Map.of(
                "A", node("A", 0, 0, Map.of()),
                "B", node("B", 100, 0, Map.of("traffic_signals", "yes")));
        Map<String, OsmWayRecord> ways = Map.of(
                "1", way("1", List.of("A", "B"), Map.of("highway", "residential")));

        Map<String, OsmNodeClassification> out = classify(res(nodes, ways), Set.of(), Set.of(), Set.of(), false);

        assertTrue(out.get("B").reasons().contains(OsmNodeReason.SIGNALIZED));
    }

    @Test
    void crossingAndSemanticNodeTagsAreHonored() {
        Map<String, OsmNodeRecord> nodes = Map.of(
                "A", node("A", 0, 0, Map.of()),
                "C", node("C", 100, 0, Map.of("crossing", "marked")),
                "G", node("G", 200, 0, Map.of("highway", "give_way")));
        Map<String, OsmWayRecord> ways = Map.of(
                "1", way("1", List.of("A", "C", "G"), Map.of("highway", "residential")));

        Map<String, OsmNodeClassification> withCrossing =
                classify(res(nodes, ways), Set.of(), Set.of(), Set.of(), true);
        assertTrue(withCrossing.get("C").reasons().contains(OsmNodeReason.CROSSING));
        assertTrue(withCrossing.get("G").reasons().contains(OsmNodeReason.SEMANTIC_NODE_TAG));

        Map<String, OsmNodeClassification> withoutCrossing =
                classify(res(nodes, ways), Set.of(), Set.of(), Set.of(), false);
        assertFalse(withoutCrossing.get("C").reasons().contains(OsmNodeReason.CROSSING));
        assertTrue(withoutCrossing.get("G").reasons().contains(OsmNodeReason.SEMANTIC_NODE_TAG));
    }
}
