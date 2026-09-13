package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.osm.OsmElementType;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationMemberRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

class OsmTopologyBuilderTest {

    private static OsmNodeRecord n(String id, double x) {
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.empty());
    }
    private static OsmWayRecord w(String id, List<String> refs) {
        return new OsmWayRecord(id, refs, OsmTagSet.of(Map.of("highway", "residential")));
    }
    private static OsmImportResult res(Map<String, OsmNodeRecord> ns, Map<String, OsmWayRecord> ws) {
        return res(ns, ws, new TreeMap<>());
    }
    private static OsmImportResult res(Map<String, OsmNodeRecord> ns, Map<String, OsmWayRecord> ws,
                                       Map<String, OsmRelationRecord> rels) {
        return new OsmImportResult(ns, ws, rels, List.of(),
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

    /**
     * Ruling B: the explicit transit-stop overload preserves a stop node the tag-derived set would
     * miss. An {@code amenity=bus_station} degree-2 node is a hint stop (importer policy) but carries
     * none of the tags the engine derives stops from, so the 3-arg engine collapses it and the 4-arg
     * (given the hint set) keeps it.
     */
    @Test
    void explicitTransitStopOverloadRetainsHintOnlyStopNode() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0));
        ns.put("B", new OsmNodeRecord("B", 100, 0, new Coord(100, 0),
                OsmTagSet.of(Map.of("amenity", "bus_station"))));
        ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B", "C")));

        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        Set<String> stops = Set.of("B");

        CollapsedTopology derived = OsmTopologyBuilder.buildSignalReady(
                res(ns, ws), cfg, OsmSimplifyOptions.defaults());
        assertFalse(derived.routingNodeIds().contains("B"),
                "tag-derived stops must not include amenity=bus_station");

        CollapsedTopology explicit = OsmTopologyBuilder.buildSignalReady(
                res(ns, ws), cfg, OsmSimplifyOptions.defaults(), stops);
        assertTrue(explicit.routingNodeIds().contains("B"),
                "explicit transit-stop set must retain the hint-only stop node");
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

    /**
     * Builds a fixture with three separate link components: a 6-direct-link car junction
     * (A-B, B-C, B-D sharing node B), a 2-direct-link isolated car stub (X-Y), and a 2-direct-link
     * isolated busway stub (P-Q). The car junction survives the cleaner's size rule on its own;
     * the small stubs exercise the transit-vs-non-transit contract.
     */
    private static OsmImportResult cleanupFixture() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0));
        ns.put("B", n("B", 100));
        ns.put("C", n("C", 200));
        ns.put("D", n("D", 300));
        ns.put("X", n("X", 100000));
        ns.put("Y", n("Y", 100100));
        ns.put("P", n("P", 200000));
        ns.put("Q", n("Q", 200100));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B")));
        ws.put("11", w("11", List.of("B", "C")));
        ws.put("12", w("12", List.of("B", "D")));
        ws.put("13", w("13", List.of("X", "Y")));
        ws.put("14", new OsmWayRecord("14", List.of("P", "Q"),
                OsmTagSet.of(Map.of("highway", "busway"))));
        return res(ns, ws);
    }

    /** Cleaning is on by default, so isolated non-routable stubs are removed and quarantined. */
    @Test
    void defaultConfigCleansIsolatedNonTransitStub() {
        CollapsedTopology t = OsmTopologyBuilder.build(cleanupFixture(),
                OsmNetworkBuildConfig.defaultConfig(), false);
        Set<String> nodeIds = t.network().getNodes().keySet().stream()
                .map(Object::toString).collect(java.util.stream.Collectors.toSet());
        assertFalse(nodeIds.contains("osm_node_X"), "non-transit stub node X must be removed");
        assertFalse(nodeIds.contains("osm_node_Y"), "non-transit stub node Y must be removed");
        // Only the main junction survives (6 directed links); both isolated stubs are quarantined.
        assertEquals(6, t.network().getLinks().size());
        assertEquals(2, t.quarantinedComponents().size(), "both isolated stubs must be quarantined");
        assertTrue(t.issues().stream().anyMatch(i -> "quarantined-component".equals(i.code())));
    }

    /**
     * Since bus is added to all car roads, the bus routable network is the main car component; an
     * isolated busway stub is a separate small bus component and is quarantined, matching pt2MATSim's
     * per-mode largest-SCC cleaning. The X-Y car stub is likewise removed.
     */
    @Test
    void cleanupQuarantinesIsolatedStubsIncludingIsolatedBusway() {
        CollapsedTopology t = OsmTopologyBuilder.build(cleanupFixture(),
                OsmNetworkBuildConfig.defaultConfigWithCleanup(), false);

        Set<String> nodeIds = t.network().getNodes().keySet().stream()
                .map(Object::toString).collect(java.util.stream.Collectors.toSet());
        assertFalse(nodeIds.contains("osm_node_X"), "non-transit stub node X must be removed");
        assertFalse(nodeIds.contains("osm_node_Y"), "non-transit stub node Y must be removed");
        assertFalse(nodeIds.contains("osm_node_P"), "isolated busway stub P must be quarantined");
        assertFalse(nodeIds.contains("osm_node_Q"), "isolated busway stub Q must be quarantined");
        assertTrue(nodeIds.contains("osm_node_A"), "main component node A must survive");
        assertTrue(nodeIds.contains("osm_node_B"), "main component node B must survive");
        assertEquals(6, t.network().getLinks().size());
    }

    /** Provenance maps must not retain removed links or nodes after cleanup. */
    @Test
    void cleanupEnabledReconcilesProvenanceMaps() {
        CollapsedTopology t = OsmTopologyBuilder.build(cleanupFixture(),
                OsmNetworkBuildConfig.defaultConfigWithCleanup(), false);

        for (String linkId : t.collapsedLinksByLinkId().keySet()) {
            assertTrue(t.network().getLinks().containsKey(
                            com.citymodeler.matsim.models.api.Id.create(linkId,
                                    com.citymodeler.matsim.models.network.Link.class)),
                    "collapsed link " + linkId + " has no surviving network link");
            assertTrue(t.geometry().containsKey(linkId), "geometry missing for " + linkId);
        }
        for (String linkId : t.geometry().keySet()) {
            assertTrue(t.collapsedLinksByLinkId().containsKey(linkId),
                    "geometry " + linkId + " has no collapsed link");
        }
        assertFalse(t.linkIdsByOsmWayId().containsKey("13"),
                "removed stub way 13 must not remain indexed");
        for (Map.Entry<String, List<String>> e : t.linkIdsByOsmWayId().entrySet()) {
            for (String linkId : e.getValue()) {
                assertTrue(t.collapsedLinksByLinkId().containsKey(linkId),
                        "way " + e.getKey() + " indexes removed link " + linkId);
            }
        }
        assertFalse(t.routingNodeIds().contains("X"), "removed routing node X must be dropped");
        assertFalse(t.routingNodeIds().contains("Y"), "removed routing node Y must be dropped");
        assertFalse(t.classification().containsKey("X"), "removed classification X must be dropped");
        assertFalse(t.classification().containsKey("Y"), "removed classification Y must be dropped");
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

    /**
     * IMPORTANT #3a: two adjacent ways whose STORED orientations oppose but whose PHYSICAL
     * directional attributes match. way10 is stored A->B (fwd 30 / bwd 50); way11 is stored C->B
     * with the tags swapped (fwd 50 / bwd 30), so the physical continuation of A->B is B->C at
     * 30 km/h and the physical continuation of B->A is C->B at 50 km/h. B is therefore genuinely
     * contractible, and the two emitted directed links must carry the physically correct speeds,
     * inverting way11's stored attributes. The pre-fix code compared stored orientation directly
     * and treated the pair as incompatible (or, with equal stored tags, merged and dropped a
     * direction); either way it failed to produce A->C=30 and C->A=50.
     */
    @Test
    void opposingStoredOrientationMergesWithInvertedDirectionalAttributes() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B"), OsmTagSet.of(Map.of(
                "highway", "residential",
                "maxspeed:forward", "30", "maxspeed:backward", "50"))));
        // Stored C->B; its forward (C->B physically) is 50 and its backward (B->C physically) is 30.
        ws.put("11", new OsmWayRecord("11", List.of("C", "B"), OsmTagSet.of(Map.of(
                "highway", "residential",
                "maxspeed:forward", "50", "maxspeed:backward", "30"))));

        Network net = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false).network();

        assertEquals(2, net.getLinks().size(), "B is physically compatible and must contract");
        Link fwd = net.getLinks().get(Id.create("sim_10_f_A_C", Link.class));
        Link rev = net.getLinks().get(Id.create("sim_10_r_A_C", Link.class));
        assertNotNull(fwd);
        assertNotNull(rev);
        assertEquals(30.0 / 3.6, fwd.getFreespeed(), 1e-9, "A->C is the 30 km/h direction");
        assertEquals(50.0 / 3.6, rev.getFreespeed(), 1e-9, "C->A is the 50 km/h direction");
    }

    /**
     * IMPORTANT #3b: the same opposing stored orientation with IDENTICAL directional tags has a
     * physical property change at B (A->B is 30 km/h, B->C is way11's 50 km/h), so the existing
     * property-change rule must retain B. The pre-fix code compared the stored orientations
     * directly, saw two equal tuples, and incorrectly contracted B.
     */
    @Test
    void opposingStoredOrientationPropertyChangeKeepsNode() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        OsmTagSet directional = OsmTagSet.of(Map.of(
                "highway", "residential",
                "maxspeed:forward", "30", "maxspeed:backward", "50"));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B"), directional));
        ws.put("11", new OsmWayRecord("11", List.of("C", "B"), directional));

        Network net = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false).network();

        assertTrue(net.getNodes().containsKey(Id.create("osm_node_B", Node.class)),
                "physical speed change at B must retain B");
        // A->B: way10 stored forward = 30. B->C: way11 stored backward = 50.
        assertEquals(30.0 / 3.6,
                net.getLinks().get(Id.create("sim_10_f_A_B", Link.class)).getFreespeed(), 1e-9);
        assertEquals(50.0 / 3.6,
                net.getLinks().get(Id.create("sim_11_f_B_C", Link.class)).getFreespeed(), 1e-9);
    }

    /**
     * IMPORTANT #2: a turn-restriction whose {@code via} member is a WAY must keep EVERY node of
     * that via way as a routing node — the interior of the restriction chain must never be swallowed
     * into one merged link. Here via way 20 is a degree-2 chain V1-V2 between the from way (10) and
     * the to way (30); both V1 and V2 must survive.
     */
    @Test
    void viaWayRestrictionKeepsEveryInteriorChainNode() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("V1", n("V1", 100));
        ns.put("V2", n("V2", 200)); ns.put("C", n("C", 300));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "V1")));
        ws.put("20", w("20", List.of("V1", "V2")));
        ws.put("30", w("30", List.of("V2", "C")));
        Map<String, OsmRelationRecord> rels = new TreeMap<>();
        rels.put("r1", new OsmRelationRecord("r1", List.of(
                new OsmRelationMemberRecord(OsmElementType.WAY, "10", "from"),
                new OsmRelationMemberRecord(OsmElementType.WAY, "20", "via"),
                new OsmRelationMemberRecord(OsmElementType.WAY, "30", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "no_left_turn"))));

        CollapsedTopology t = OsmTopologyBuilder.buildSignalReady(
                res(ns, ws, rels), OsmNetworkBuildConfig.materializeGeometryConfig(),
                OsmSimplifyOptions.defaults());

        assertTrue(t.routingNodeIds().contains("V1"), "via-way interior node V1 must survive");
        assertTrue(t.routingNodeIds().contains("V2"), "via-way interior node V2 must survive");
        assertTrue(t.network().getNodes().containsKey(Id.create("osm_node_V1", Node.class)));
        assertTrue(t.network().getNodes().containsKey(Id.create("osm_node_V2", Node.class)));
        // The chain must not be swallowed: no A->C merged link may exist.
        assertFalse(t.collapsedLinksByLinkId().containsKey("sim_10_f_A_C"));
        // Bidirectional ways: A<->V1, V1<->V2, V2<->C = 6 directed links.
        assertEquals(6, t.network().getLinks().size(),
                "three retained degree-2 nodes chain into three directed link pairs; no A->C shortcut");
    }

    /**
     * IMPORTANT #4: a routing node must never survive with no incident emitted link. Here B is kept
     * intrinsically (barrier) but the whole way is geometrically degenerate (A, B, C all zero
     * length), so both zero-length spans are dropped by emitLink. The unconditional reconcile must
     * drop the now-isolated routing nodes and emit a deterministic WARNING; the pre-fix code left
     * B (and A, C) in the routing set with zero incident links.
     */
    @Test
    void isolatedRoutingNodesAreReconciledOut() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0));
        ns.put("B", new OsmNodeRecord("B", 0, 0, new Coord(0, 0),
                OsmTagSet.of(Map.of("barrier", "gate"))));
        ns.put("C", n("C", 0));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B", "C"),
                OsmTagSet.of(Map.of("highway", "residential", "oneway", "yes"))));

        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false);

        assertEquals(0, t.network().getLinks().size(), "fully degenerate way emits no link");
        Set<String> referenced = new TreeSet<>();
        for (Link l : t.network().getLinks().values()) {
            referenced.add(l.getFromNode().getId().toString());
            referenced.add(l.getToNode().getId().toString());
        }
        for (String osmId : t.routingNodeIds()) {
            assertTrue(referenced.contains("osm_node_" + osmId),
                    "routing node " + osmId + " has no incident emitted link");
        }
        assertFalse(t.routingNodeIds().contains("B"),
                "isolated intrinsic routing node B must be reconciled out");
        assertFalse(t.classification().containsKey("B"),
                "classification must be reconciled with the routing set");
        assertTrue(t.issues().stream().anyMatch(i -> "isolated-routing-node".equals(i.code())),
                "a deterministic isolated-routing-node WARNING must be emitted");
    }

    /** A multi-way merged link preserves every source way, node, name and its full WKT geometry. */
    @Test
    void crossWayMergedLinkCarriesFullProvenance() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100));
        ns.put("C", n("C", 200)); ns.put("D", n("D", 300));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("highway", "residential", "name", "First St"))));
        ws.put("20", new OsmWayRecord("20", List.of("B", "C"),
                OsmTagSet.of(Map.of("highway", "residential", "name", "Second Ave"))));
        ws.put("30", new OsmWayRecord("30", List.of("C", "D"),
                OsmTagSet.of(Map.of("highway", "residential", "name", "Second Ave"))));

        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.defaultConfig(), false);

        Link fwd = t.network().getLinks().get(Id.create("sim_10_f_A_D", Link.class));
        assertNotNull(fwd);
        assertEquals("10,20,30", fwd.getAttributes().getAttribute("osm:sourceWays"));
        assertEquals("A,B,C,D", fwd.getAttributes().getAttribute("osm:sourceNodes"));
        assertEquals("First St", fwd.getAttributes().getAttribute("osm:name"));
        assertEquals("First St,Second Ave", fwd.getAttributes().getAttribute("osm:sourceNames"));
        assertEquals("LINESTRING (0.0 0.0, 100.0 0.0, 200.0 0.0, 300.0 0.0)",
                fwd.getAttributes().getAttribute("osm:geometry"));

        // Both travel directions of one physical link agree on name and source ways.
        Link rev = t.network().getLinks().get(Id.create("sim_10_r_A_D", Link.class));
        assertEquals("First St", rev.getAttributes().getAttribute("osm:name"));
        assertEquals("10,20,30", rev.getAttributes().getAttribute("osm:sourceWays"));
        assertEquals("D,C,B,A", rev.getAttributes().getAttribute("osm:sourceNodes"));
    }

    /** A single-way link reports one way/name and omits the multi-name attribute. */
    @Test
    void singleWayLinkOmitsMultiNameAttribute() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B", "C"),
                OsmTagSet.of(Map.of("highway", "residential", "name", "Solo Road"))));

        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.defaultConfig(), false);

        Link fwd = t.network().getLinks().get(Id.create("sim_10_f_A_C", Link.class));
        assertEquals("10", fwd.getAttributes().getAttribute("osm:sourceWays"));
        assertEquals("Solo Road", fwd.getAttributes().getAttribute("osm:name"));
        assertNull(fwd.getAttributes().getAttribute("osm:sourceNames"));
    }
}
