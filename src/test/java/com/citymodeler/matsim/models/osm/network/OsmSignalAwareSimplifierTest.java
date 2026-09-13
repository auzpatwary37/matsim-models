package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks;

final class OsmSignalAwareSimplifierTest {

    private static OsmSimplifiedNetwork simplifyCrossroads() {
        OsmImportResult r = SignalReadyFixtures.crossroads();
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        return OsmSignalAwareSimplifier.simplify(mat, r, cfg, OsmSimplifyOptions.defaults());
    }

    private static OsmSimplifiedNetwork simplify(OsmImportResult r) {
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        return OsmSignalAwareSimplifier.simplify(mat, r, cfg, OsmSimplifyOptions.defaults());
    }

    @Test
    void collapsesGeometryOnlyNodesAndKeepsSignalizedAndEndpoints() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        var net = s.network();
        // Kept: W, E, S, N. Collapsed: Wm, Em, Sm.
        assertEquals(4, net.getNodes().size());
        assertTrue(net.getNodes().containsKey(Id.create("osm_node_N", Node.class)));
        // Bidirectional arms W-N, N-E, N-S => 6 merged links.
        assertEquals(6, net.getLinks().size());
        assertEquals(3, s.report().collapsedGeometryNodes());
    }

    @Test
    void mergedLinkCarriesSummedLengthAndGeometry() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        // Canonical engine id: the W->N travel direction of the W-N physical span.
        Link ln = s.network().getLinks().get(Id.create("sim_10_r_N_W", Link.class));
        assertNotNull(ln);
        assertEquals(100.0, ln.getLength(), 0.001);
        assertTrue(s.geometryStore().geometryForLink("sim_10_r_N_W").isPresent());
        // 2 collapsed OSM segments => 3 polyline vertices.
        assertEquals(3, s.geometryStore().geometryForLink("sim_10_r_N_W").get().points().size());
        assertEquals(2, s.collapsedLink("sim_10_r_N_W").segmentCount());
    }

    @Test
    void turnRestrictionSurvivesSimplification() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        DisallowedNextLinks dnl = s.perLinkDisallowed().get("sim_10_r_N_W");
        assertNotNull(dnl);
        assertTrue(dnl.isDisallowed("car", List.of("sim_20_r_E_N")));
        assertFalse(dnl.isDisallowed("car", List.of("sim_30_f_N_S")));
    }

    @Test
    void isDeterministic() {
        OsmSimplifiedNetwork a = simplifyCrossroads();
        OsmSimplifiedNetwork b = simplifyCrossroads();
        assertEquals(a.network().getLinks().keySet(), b.network().getLinks().keySet());
        assertEquals(a.collapsedLinksByLinkId().keySet(), b.collapsedLinksByLinkId().keySet());
    }

    /** Review: repeated interior nodes must not corrupt positional source-segment provenance. */
    @Test
    void repeatedNodeWayKeepsPositionalSourceSegments() {
        OsmSimplifiedNetwork s = simplify(SignalReadyFixtures.wayWithRepeatedNode());
        // P1 is referenced twice and has degree 3, so the engine correctly keeps it as a branch.
        assertTrue(s.network().getNodes().containsKey(Id.create("osm_node_P1", Node.class)));
        // The P1->P3 span carries positional source segment 3, not the P1->P2 reverse's index.
        OsmCollapsedLink toP3 = s.collapsedLink("sim_50_f_P1_P3");
        assertNotNull(toP3);
        assertEquals(List.of(3), toP3.sourceSegments().stream().map(OsmLinkRef::segmentIndex).toList());
        assertTrue(toP3.sourceSegments().stream().allMatch(OsmLinkRef::forward));
        OsmCollapsedLink toP2 = s.collapsedLink("sim_50_f_P1_P2");
        assertNotNull(toP2);
        assertEquals(List.of(1), toP2.sourceSegments().stream().map(OsmLinkRef::segmentIndex).toList());
        // Finding 2: the second, degenerate span (segment 2 traversed P1->P2) is dropped, but not
        // silently — a deterministic WARNING names the way and the canonical directed span.
        assertTrue(s.issues().stream().anyMatch(i ->
                        "degenerate-span".equals(i.code())
                                && i.message().contains("sim_50_f_P1_P2")),
                "dropped degenerate span must be reported");
    }

    /** Review: a degree-2 node between two adjacent ways with differing lanes must survive. */
    @Test
    void semanticBoundaryNodeSurvivesBetweenAdjacentWays() {
        OsmSimplifiedNetwork s = simplify(SignalReadyFixtures.semanticBoundaryNode());
        var net = s.network();
        assertTrue(net.getNodes().containsKey(Id.create("osm_node_M", Node.class)));
        Link left = net.getLinks().get(Id.create("sim_30_f_L_M", Link.class));
        Link right = net.getLinks().get(Id.create("sim_31_f_M_R", Link.class));
        assertNotNull(left);
        assertNotNull(right);
        assertEquals("2", left.getAttributes().getAttribute("osm:tag:lanes"));
        assertEquals("4", right.getAttributes().getAttribute("osm:tag:lanes"));
    }

    /** Cross-way: a degree-2 way split must not survive signal-ready simplification. */
    @Test
    void splitsAcrossWaysCollapseInSignalReady() {
        var ns = new java.util.TreeMap<String, com.citymodeler.matsim.models.osm.model.OsmNodeRecord>();
        ns.put("A", new com.citymodeler.matsim.models.osm.model.OsmNodeRecord(
                "A", 0, 0, new com.citymodeler.matsim.models.api.Coord(0, 0), com.citymodeler.matsim.models.osm.OsmTagSet.empty()));
        ns.put("B", new com.citymodeler.matsim.models.osm.model.OsmNodeRecord(
                "B", 100, 0, new com.citymodeler.matsim.models.api.Coord(100, 0), com.citymodeler.matsim.models.osm.OsmTagSet.empty()));
        ns.put("C", new com.citymodeler.matsim.models.osm.model.OsmNodeRecord(
                "C", 200, 0, new com.citymodeler.matsim.models.api.Coord(200, 0), com.citymodeler.matsim.models.osm.OsmTagSet.empty()));
        var ws = new java.util.TreeMap<String, com.citymodeler.matsim.models.osm.model.OsmWayRecord>();
        ws.put("10", new com.citymodeler.matsim.models.osm.model.OsmWayRecord("10",
                java.util.List.of("A", "B"), com.citymodeler.matsim.models.osm.OsmTagSet.of(java.util.Map.of("highway", "residential"))));
        ws.put("11", new com.citymodeler.matsim.models.osm.model.OsmWayRecord("11",
                java.util.List.of("B", "C"), com.citymodeler.matsim.models.osm.OsmTagSet.of(java.util.Map.of("highway", "residential"))));
        var r = new com.citymodeler.matsim.models.osm.OsmImportResult(ns, ws,
                new java.util.TreeMap<>(), java.util.List.of(),
                com.citymodeler.matsim.models.osm.OsmProvenance.defaultFor("f.osm", "EPSG:3857"));

        var s = new com.citymodeler.matsim.models.osm.network.OsmMatsimNetworkBuilder()
                .buildSignalReady(r, com.citymodeler.matsim.models.osm.network.OsmNetworkBuildConfig.defaultConfig());

        assertEquals(2, s.network().getNodes().size());
        assertFalse(s.network().getNodes().containsKey(
                com.citymodeler.matsim.models.api.Id.create("osm_node_B",
                        com.citymodeler.matsim.models.network.Node.class)));
    }

    /**
     * Finding 1: importer-policy warnings added by {@code OsmMatsimNetworkBuilder.addWayWarnings}
     * (here {@code dynamic-oneway}) must survive simplification. The simplifier seeds its issues from
     * the materialized result, which carries them, rather than from the engine's raw issues.
     */
    @Test
    void preservesImporterPolicyWarnings() {
        OsmSimplifiedNetwork s = simplify(SignalReadyFixtures.dynamicOnewayWay());
        assertTrue(s.issues().stream().anyMatch(i -> "dynamic-oneway".equals(i.code())),
                "simplified network must retain the importer dynamic-oneway warning");
    }

    /** Finding 1 (at minimum): simplify must preserve every issue present on the materialized result. */
    @Test
    void preservesMaterializedIssues() {
        OsmImportResult r = SignalReadyFixtures.crossroads();
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        OsmSimplifiedNetwork s = OsmSignalAwareSimplifier.simplify(
                mat, r, cfg, OsmSimplifyOptions.defaults());
        for (var issue : mat.issues()) {
            assertTrue(s.issues().contains(issue),
                    "materialized issue not preserved: " + issue.code() + " -> " + issue.message());
        }
    }

    /** Review: carried-over lane tags must be explicitly marked raw whole-way provenance. */
    @Test
    void copiedLaneTagsAreMarkedRawWholeWayProvenance() {
        OsmSimplifiedNetwork s = simplify(SignalReadyFixtures.crossroadsWithTurnLanes());
        Link withLanes = s.network().getLinks().get(Id.create("sim_10_r_N_W", Link.class));
        assertNotNull(withLanes);
        assertEquals("2", withLanes.getAttributes().getAttribute("osm:tag:lanes"));
        assertEquals("through|left", withLanes.getAttributes().getAttribute("osm:tag:turn:lanes"));
        assertEquals("raw-source", withLanes.getAttributes().getAttribute("osm:laneTags.scope"));
        assertEquals("whole-way", withLanes.getAttributes().getAttribute("osm:laneTags.applicability"));

        Link noLanes = s.network().getLinks().get(Id.create("sim_20_f_E_N", Link.class));
        assertNotNull(noLanes);
        assertEquals(null, noLanes.getAttributes().getAttribute("osm:laneTags.scope"));
    }
}
