package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

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

class OsmRoutingNodeSelectorTest {

    private static OsmNodeRecord node(String id, double x, String... kv) {
        Map<String, String> t = new java.util.TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) t.put(kv[i], kv[i + 1]);
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.of(t));
    }

    private static OsmImportResult result(Map<String, OsmNodeRecord> nodes, Map<String, OsmWayRecord> ways) {
        return new OsmImportResult(nodes, ways, new java.util.TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));
    }

    private static OsmWayRecord way(String id, List<String> refs, String... kv) {
        Map<String, String> t = new java.util.TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) t.put(kv[i], kv[i + 1]);
        return new OsmWayRecord(id, refs, OsmTagSet.of(t));
    }

    /** The core invariant: a plain degree-2 node is contractible whether or not it splits a way. */
    @Test
    void plainDegree2NodeIsContractibleAcrossWays() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100)); // degree 2, no tags, way boundary
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential"));
        ways.put("11", way("11", List.of("B", "C"), "highway", "residential"));

        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);

        assertTrue(OsmRoutingNodeSelector.contractible(g, "B"));
        assertEquals(Set.of("A", "C"), OsmRoutingNodeSelector.select(g, cls, false));
    }

    /** A node where speed changes must be kept so the two links can differ. */
    @Test
    void propertyChangeNodeIsKept() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential", "maxspeed", "30"));
        ways.put("11", way("11", List.of("B", "C"), "highway", "residential", "maxspeed", "50"));

        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);

        assertFalse(OsmRoutingNodeSelector.contractible(g, "B"));
        assertTrue(OsmRoutingNodeSelector.select(g, cls, false).contains("B"));
    }

    /** A signal at a degree-2 node is always retained. */
    @Test
    void signalNodeIsKept() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100, "highway", "traffic_signals"));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential"));
        ways.put("11", way("11", List.of("B", "C"), "highway", "residential"));

        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);

        // contractible(...) is purely structural (no access to node tags); the signal is kept by
        // select(...) via the intrinsic classification reason.
        assertTrue(cls.get("B").reasons().contains(OsmNodeReason.SIGNALIZED));
        assertTrue(OsmRoutingNodeSelector.select(g, cls, false).contains("B"));
    }

    /** A degree-2 U-shaped oneway node (directions oppose) must not contract. */
    @Test
    void opposingOnewayDegree2NodeIsKept() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        nodes.put("C", node("C", 100, "y", "1"));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential", "oneway", "yes"));
        ways.put("11", way("11", List.of("C", "B"), "highway", "residential", "oneway", "yes"));

        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);

        assertFalse(OsmRoutingNodeSelector.contractible(g, "B"));
    }

    @Test
    void keepAllReturnsEveryNode() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential"));
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        assertEquals(Set.of("A", "B"), OsmRoutingNodeSelector.select(g, Map.of(), true));
    }
}
