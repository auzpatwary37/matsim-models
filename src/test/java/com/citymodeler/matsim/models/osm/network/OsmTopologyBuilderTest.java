package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

class OsmTopologyBuilderTest {

    private static OsmNodeRecord n(String id, double x) {
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.empty());
    }
    private static OsmWayRecord w(String id, List<String> refs) {
        return new OsmWayRecord(id, refs, OsmTagSet.of(Map.of("highway", "residential")));
    }
    private static OsmImportResult res(Map<String, OsmNodeRecord> ns, Map<String, OsmWayRecord> ws) {
        return new OsmImportResult(ns, ws, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));
    }
    /** Topology signature: sorted "from->to" for every link, independent of ids. */
    private static Set<String> signature(Network net) {
        Set<String> sig = new TreeSet<>();
        net.getLinks().values().forEach(l ->
                sig.add(l.getFromNode().getId() + "->" + l.getToNode().getId()));
        return sig;
    }

    @Test
    void collapsesPlainDegree2NodeIntoOneLink() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B", "C")));

        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false);

        assertEquals(Set.of("osm_node_A", "osm_node_C"), t.network().getNodes().keySet().stream()
                .map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, t.network().getLinks().size()); // forward + reverse
        assertTrue(t.geometry().containsKey("sim_10_f_A_C"));
        assertEquals(3, t.geometry().get("sim_10_f_A_C").points().size()); // A,B,C preserved
    }

    /**
     * THE invariant: the same physical road represented as one way and as two ways split at a
     * degree-2 node must produce identical MATSim topology after contraction.
     */
    @Test
    void oneWayAndSplitWayProduceIdenticalTopology() {
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();

        Map<String, OsmNodeRecord> ns1 = new TreeMap<>();
        ns1.put("A", n("A", 0)); ns1.put("B", n("B", 100)); ns1.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws1 = new TreeMap<>();
        ws1.put("10", w("10", List.of("A", "B", "C")));

        Map<String, OsmNodeRecord> ns2 = new TreeMap<>();
        ns2.put("A", n("A", 0)); ns2.put("B", n("B", 100)); ns2.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws2 = new TreeMap<>();
        ws2.put("10", w("10", List.of("A", "B")));
        ws2.put("11", w("11", List.of("B", "C")));

        Network one = OsmTopologyBuilder.build(res(ns1, ws1), cfg, false).network();
        Network two = OsmTopologyBuilder.build(res(ns2, ws2), cfg, false).network();

        assertEquals(one.getNodes().size(), two.getNodes().size());
        assertEquals(one.getLinks().size(), two.getLinks().size());
        assertEquals(signature(one), signature(two));
    }

    @Test
    void crossWayCollapsedLinkListsBothSourceWays() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B")));
        ws.put("11", w("11", List.of("B", "C")));

        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false);
        OsmCollapsedLink cl = t.collapsedLinksByLinkId().get("sim_10_f_A_C");
        assertNotNull(cl);
        assertEquals(List.of("10", "11"), cl.sourceOsmWayIds());
        assertEquals(2, cl.segmentCount());
        // the merged link is indexed under BOTH source ways
        assertTrue(t.linkIdsByOsmWayId().get("10").contains("sim_10_f_A_C"));
        assertTrue(t.linkIdsByOsmWayId().get("11").contains("sim_10_f_A_C"));
    }

    @Test
    void keepAllModeDoesNotCollapse() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B", "C")));
        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), true);
        assertEquals(3, t.network().getNodes().size());
    }

    /** Finding 1: reverse links must carry the way's backward-resolved freespeed/lanes. */
    @Test
    void reverseLinkCarriesBackwardResolvedAttributes() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B", "C"), OsmTagSet.of(Map.of(
                "highway", "residential",
                "maxspeed:forward", "30", "maxspeed:backward", "50",
                "lanes:forward", "1", "lanes:backward", "2"))));

        Network net = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false).network();

        var fwd = net.getLinks().get(com.citymodeler.matsim.models.api.Id.createLinkId("sim_10_f_A_C"));
        var rev = net.getLinks().get(com.citymodeler.matsim.models.api.Id.createLinkId("sim_10_r_A_C"));
        assertNotNull(fwd);
        assertNotNull(rev);
        assertEquals(30.0 / 3.6, fwd.getFreespeed(), 1e-9);
        assertEquals(50.0 / 3.6, rev.getFreespeed(), 1e-9);
        assertEquals(1.0, fwd.getNumberOfLanes(), 1e-9);
        assertEquals(2.0, rev.getNumberOfLanes(), 1e-9);
    }

    /** Finding 2: both directions share canonical ids/provenance, independent of travel order. */
    @Test
    void bothDirectionsShareCanonicalProvenanceAcrossWays() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B")));
        ws.put("11", w("11", List.of("B", "C")));

        Network net = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false).network();

        var fwd = net.getLinks().get(com.citymodeler.matsim.models.api.Id.createLinkId("sim_10_f_A_C"));
        var rev = net.getLinks().get(com.citymodeler.matsim.models.api.Id.createLinkId("sim_10_r_A_C"));
        assertNotNull(fwd);
        assertNotNull(rev);
        assertEquals("10", fwd.getAttributes().getAttribute("osm:wayId"));
        assertEquals("10", rev.getAttributes().getAttribute("osm:wayId"));
        assertEquals("osm_node_A", fwd.getFromNode().getId().toString());
        assertEquals("osm_node_C", fwd.getToNode().getId().toString());
        assertEquals("osm_node_C", rev.getFromNode().getId().toString());
        assertEquals("osm_node_A", rev.getToNode().getId().toString());
    }

    /** Engine-level directionality: a oneway road yields exactly one directed link. */
    @Test
    void onewayRoadYieldsSingleDirectedLink() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B", "C"),
                OsmTagSet.of(Map.of("highway", "residential", "oneway", "yes"))));

        Network net = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false).network();

        assertEquals(1, net.getLinks().size(), "oneway road must produce exactly one directed link");
        var fwd = net.getLinks().get(com.citymodeler.matsim.models.api.Id.create(
                "sim_10_f_A_C", com.citymodeler.matsim.models.network.Link.class));
        assertNotNull(fwd, "the single link is the forward direction A->C");
        assertEquals("osm_node_A", fwd.getFromNode().getId().toString());
        assertEquals("osm_node_C", fwd.getToNode().getId().toString());
        assertEquals(2, net.getNodes().size());
    }

    /** Engine-level directionality: a bidirectional road emits opposite directed links. */
    @Test
    void bidirectionalRoadYieldsOppositeDirectedLinks() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B", "C"))); // no oneway -> bidirectional

        Network net = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false).network();

        assertEquals(2, net.getLinks().size());
        var fwd = net.getLinks().get(com.citymodeler.matsim.models.api.Id.create(
                "sim_10_f_A_C", com.citymodeler.matsim.models.network.Link.class));
        var rev = net.getLinks().get(com.citymodeler.matsim.models.api.Id.create(
                "sim_10_r_A_C", com.citymodeler.matsim.models.network.Link.class));
        assertNotNull(fwd); assertNotNull(rev);
        // Each MATSim link is directed: opposite endpoints.
        assertEquals("osm_node_A", fwd.getFromNode().getId().toString());
        assertEquals("osm_node_C", fwd.getToNode().getId().toString());
        assertEquals("osm_node_C", rev.getFromNode().getId().toString());
        assertEquals("osm_node_A", rev.getToNode().getId().toString());
    }
}
