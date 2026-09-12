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
        Link ln = s.network().getLinks().get(Id.create("sim_10_f_W_N", Link.class));
        assertNotNull(ln);
        assertEquals(100.0, ln.getLength(), 0.001);
        assertTrue(s.geometryStore().geometryForLink("sim_10_f_W_N").isPresent());
        // 2 collapsed OSM segments => 3 polyline vertices.
        assertEquals(3, s.geometryStore().geometryForLink("sim_10_f_W_N").get().points().size());
        assertEquals(2, s.collapsedLink("sim_10_f_W_N").segmentCount());
    }

    @Test
    void turnRestrictionSurvivesSimplification() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        DisallowedNextLinks dnl = s.perLinkDisallowed().get("sim_10_f_W_N");
        assertNotNull(dnl);
        assertTrue(dnl.isDisallowed("car", List.of("sim_20_f_N_E")));
        assertFalse(dnl.isDisallowed("car", List.of("sim_30_r_N_S")));
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
        OsmCollapsedLink cl = s.collapsedLink("sim_50_f_P0_P3");
        assertNotNull(cl);
        assertEquals(4, cl.segmentCount());
        List<Integer> segs = cl.sourceSegments().stream().map(OsmLinkRef::segmentIndex).toList();
        assertEquals(List.of(0, 1, 2, 3), segs);
        assertTrue(cl.sourceSegments().stream().allMatch(OsmLinkRef::forward));
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

    /** Review: carried-over lane tags must be explicitly marked raw whole-way provenance. */
    @Test
    void copiedLaneTagsAreMarkedRawWholeWayProvenance() {
        OsmSimplifiedNetwork s = simplify(SignalReadyFixtures.crossroadsWithTurnLanes());
        Link withLanes = s.network().getLinks().get(Id.create("sim_10_f_W_N", Link.class));
        assertNotNull(withLanes);
        assertEquals("2", withLanes.getAttributes().getAttribute("osm:tag:lanes"));
        assertEquals("through|left", withLanes.getAttributes().getAttribute("osm:tag:turn:lanes"));
        assertEquals("raw-source", withLanes.getAttributes().getAttribute("osm:laneTags.scope"));
        assertEquals("whole-way", withLanes.getAttributes().getAttribute("osm:laneTags.applicability"));

        Link noLanes = s.network().getLinks().get(Id.create("sim_20_f_N_E", Link.class));
        assertNotNull(noLanes);
        assertEquals(null, noLanes.getAttributes().getAttribute("osm:laneTags.scope"));
    }
}
