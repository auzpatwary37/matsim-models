package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;
import com.citymodeler.matsim.models.api.Coord;

class OsmSegmentGraphTest {

    private static OsmNodeRecord node(String id, double x) {
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.empty());
    }

    private static OsmImportResult result(Map<String, OsmNodeRecord> nodes,
                                          Map<String, OsmWayRecord> ways) {
        return new OsmImportResult(nodes, ways, new java.util.TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("fixture.osm", "EPSG:3857"));
    }

    @Test
    void buildsOneSegmentPerConsecutivePair() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", new OsmWayRecord("10", List.of("A", "B", "C"),
                OsmTagSet.of(Map.of("highway", "residential"))));

        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways),
                OsmNetworkBuildConfig.materializeGeometryConfig());

        assertEquals(2, g.segments().size());
        assertEquals(Set.of("A", "B", "C"), g.nodeIds());
        assertEquals(2, g.segmentsFrom("B").size());
        assertEquals(1, g.segmentsFrom("A").size());
    }

    @Test
    void onewaySegmentAllowsOnlyForwardTravel() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("highway", "residential", "oneway", "yes"))));

        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways),
                OsmNetworkBuildConfig.materializeGeometryConfig());
        OsmSegmentGraph.Segment s = g.segments().get(0);

        assertTrue(s.forwardAllowed());
        assertFalse(s.backwardAllowed());
        assertTrue(g.allowsTravel(s, "A", "B"));
        assertFalse(g.allowsTravel(s, "B", "A"));
    }

    /** Same road split at a degree-2 node yields two consecutive atomic segments. */
    @Test
    void splitRoadYieldsTwoConsecutiveSegments() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("highway", "residential"))));
        ways.put("11", new OsmWayRecord("11", List.of("B", "C"),
                OsmTagSet.of(Map.of("highway", "residential"))));

        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways),
                OsmNetworkBuildConfig.materializeGeometryConfig());

        assertEquals(2, g.segments().size());
        assertEquals(2, g.segmentsFrom("B").size());
    }
}
