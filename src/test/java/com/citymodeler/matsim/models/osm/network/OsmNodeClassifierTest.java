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

    @Test
    void keepsEndpointsSharedSignalizedAndViaNodesCollapsesPlainInterior() {
        Map<String, OsmNodeRecord> nodes = Map.of(
                "W", node("W", 0, 100, Map.of()),
                "Wm", node("Wm", 50, 100, Map.of()),
                "N", node("N", 100, 100, Map.of("highway", "traffic_signals")),
                "E", node("E", 200, 100, Map.of()));
        Map<String, OsmWayRecord> ways = Map.of(
                "10", way("10", List.of("W", "Wm", "N"), Map.of("highway", "residential")),
                "20", way("20", List.of("N", "E"), Map.of("highway", "residential")));
        OsmImportResult res = res(nodes, ways);

        Map<String, OsmNodeClassification> out = OsmNodeClassifier.classify(
                res, Set.of("10", "20"), Set.of(), Set.of("N"), false, 30.0, Set.of());

        assertTrue(out.get("W").keep());
        assertTrue(out.get("E").keep());
        assertTrue(out.get("N").keep());
        assertFalse(out.get("Wm").keep());
        assertTrue(out.get("N").reasons().contains(OsmNodeReason.SIGNALIZED));
        assertTrue(out.get("N").reasons().contains(OsmNodeReason.TURN_RESTRICTION_VIA));
        assertTrue(out.get("Wm").reasons().isEmpty());
    }

    @Test
    void plainTrafficSignalTagAlsoMarksSignalized() {
        Map<String, OsmNodeRecord> nodes = Map.of(
                "A", node("A", 0, 0, Map.of()),
                "B", node("B", 100, 0, Map.of("traffic_signals", "yes")));
        Map<String, OsmWayRecord> ways = Map.of(
                "1", way("1", List.of("A", "B"), Map.of("highway", "residential")));

        Map<String, OsmNodeClassification> out = OsmNodeClassifier.classify(
                res(nodes, ways), Set.of("1"), Set.of(), Set.of(), false, 30.0, Set.of());

        assertTrue(out.get("B").reasons().contains(OsmNodeReason.SIGNALIZED));
    }

    @Test
    void transitStopAndExplicitPreserveAreHonored() {
        Map<String, OsmNodeRecord> nodes = Map.of(
                "A", node("A", 0, 0, Map.of()),
                "B", node("B", 100, 0, Map.of()),
                "C", node("C", 200, 0, Map.of()));
        Map<String, OsmWayRecord> ways = Map.of(
                "1", way("1", List.of("A", "B", "C"), Map.of("highway", "residential")));

        Map<String, OsmNodeClassification> out = OsmNodeClassifier.classify(
                res(nodes, ways), Set.of("1"), Set.of("B"), Set.of(), false, 30.0, Set.of("C"));

        assertTrue(out.get("B").reasons().contains(OsmNodeReason.TRANSIT_STOP));
        assertTrue(out.get("C").reasons().contains(OsmNodeReason.EXPLICIT_PRESERVE));
        assertTrue(out.get("C").keep());
    }
}
