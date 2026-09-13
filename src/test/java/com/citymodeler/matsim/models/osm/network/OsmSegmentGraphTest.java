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

    private static OsmNodeRecord node(String id, double x, double y) {
        return new OsmNodeRecord(id, x, y, new Coord(x, y), OsmTagSet.empty());
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

    /**
     * Two parallel tram ways with coincident endpoints are one physical corridor; with the default
     * config the duplicate is dropped so direction is not double-counted. With collapse disabled both
     * survive (literal OSM semantics).
     */
    @Test
    void parallelTransitTracksAreCollapsedByDefault() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("railway", "tram"))));
        ways.put("20", new OsmWayRecord("20", List.of("A", "B"),
                OsmTagSet.of(Map.of("railway", "tram"))));

        OsmSegmentGraph collapsed = OsmSegmentGraph.build(result(nodes, ways),
                OsmNetworkBuildConfig.defaultConfig());
        assertEquals(1, collapsed.segments().size(),
                "parallel tram tracks collapse to one segment by default");

        OsmSegmentGraph literal = OsmSegmentGraph.build(result(nodes, ways),
                OsmNetworkBuildConfig.materializeGeometryConfig());
        assertEquals(2, literal.segments().size(),
                "materialize config keeps literal OSM semantics (no collapse)");
    }

    /**
     * Two tracks that share endpoints but diverge in the middle (different intermediate geometry) are
     * distinct infrastructure and must both survive: endpoint proximity alone is not equivalence.
     */
    @Test
    void parallelTracksWithDistinctIntermediateGeometryBothSurvive() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0, 0));
        nodes.put("B", node("B", 100, 0));
        nodes.put("Mid1", node("Mid1", 50, 10));
        nodes.put("Mid2", node("Mid2", 50, 90));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", new OsmWayRecord("10", List.of("A", "Mid1", "B"),
                OsmTagSet.of(Map.of("railway", "tram"))));
        ways.put("20", new OsmWayRecord("20", List.of("A", "Mid2", "B"),
                OsmTagSet.of(Map.of("railway", "tram"))));

        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways),
                OsmNetworkBuildConfig.defaultConfig());
        assertEquals(4, g.segments().size(),
                "tracks with distinct intermediate geometry must not be collapsed");
    }
}
