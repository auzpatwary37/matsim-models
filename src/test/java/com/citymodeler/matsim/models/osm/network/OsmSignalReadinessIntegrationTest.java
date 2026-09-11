package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;

final class OsmSignalReadinessIntegrationTest {

    private static OsmSimplifiedNetwork simplify(OsmImportResult r) {
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        return OsmSignalAwareSimplifier.simplify(mat, r, cfg, OsmSimplifyOptions.defaults());
    }

    private static Optional<JunctionSignalDescriptor> junction(OsmSimplifiedNetwork s, String osmId) {
        return Optional.ofNullable(s.junctionAt("osm_node_" + osmId));
    }

    @Test
    void fullPipelinePreservesSemanticsAndIsSignalReady() {
        OsmSimplifiedNetwork s = simplify(SignalReadyFixtures.crossroads());
        var report = s.report();

        assertEquals(1, report.candidateSignalizedJunctions());
        assertEquals(1, report.confirmedSignalizedJunctions());
        assertEquals(1, report.preservedSignalNodes());
        assertEquals(3, report.collapsedGeometryNodes());
        assertEquals(4, report.preservedSemanticNodes());
        assertEquals(0, report.highDegreeNonSignalizedIntersections());
        assertTrue(report.ok());

        // Restricted movement survives simplification.
        JunctionSignalDescriptor j = junction(s, "N").orElseThrow();
        SignalizedMovement m = j.movements().stream()
                .filter(x -> x.incomingLinkId().equals("sim_10_f_W_N")
                        && x.outgoingLinkId().equals("sim_20_f_N_E"))
                .findFirst().orElseThrow();
        assertTrue(m.fullyRestricted());

        // No structural (WARN) findings for a well-formed junction.
        assertTrue(s.report().issues().stream().noneMatch(i -> i.severity() == OsmIssueSeverity.WARNING));
    }

    @Test
    void turnLaneDataIsCarriedOnMergedLinksAndSurfaced() {
        OsmSimplifiedNetwork s = simplify(SignalReadyFixtures.crossroadsWithTurnLanes());

        // Lane tags carried onto the merged link for the west arm.
        var link = s.network().getLinks().get(
                com.citymodeler.matsim.models.api.Id.create("sim_10_f_W_N",
                        com.citymodeler.matsim.models.network.Link.class));
        assertEquals("2", link.getAttributes().getAttribute("osm:tag:lanes"));
        assertEquals("through|left", link.getAttributes().getAttribute("osm:tag:turn:lanes"));

        // Ambiguity surfaced explicitly; movement count without lane decomposition is reported.
        assertTrue(s.report().issues().stream().anyMatch(i -> "ambiguous-lane-tags".equals(i.code())));
        assertTrue(s.report().movementsWithoutLaneInfo() > 0);
        assertTrue(s.report().ok());
    }

    @Test
    void nonSignalizedHighDegreeNodeIsReportedInformationally() {
        OsmImportResult r = crossroadsWithoutSignal();
        OsmSimplifiedNetwork s = simplify(r);

        // N is a 6-leg intersection but NOT signalized => informational finding, not a junction.
        assertEquals(0, s.signalizedJunctions().size());
        assertEquals(1, s.report().highDegreeNonSignalizedIntersections());
        assertTrue(s.report().issues().stream().anyMatch(i ->
                "non-signalized-high-degree".equals(i.code())
                        && i.severity() == OsmIssueSeverity.INFO));
    }

    @Test
    void isDeterministicIncludingReport() {
        OsmImportResult r = SignalReadyFixtures.crossroadsWithTurnLanes();
        OsmSimplifiedNetwork a = simplify(r);
        OsmSimplifiedNetwork b = simplify(r);
        assertEquals(a.network().getLinks().keySet(), b.network().getLinks().keySet());
        assertEquals(a.signalizedJunctions().size(), b.signalizedJunctions().size());
        assertEquals(a.report().summary(), b.report().summary());
        assertEquals(a.issues(), b.issues());
    }

    private static OsmImportResult crossroadsWithoutSignal() {
        // Same topology as crossroads() but N has no traffic-signal tag.
        java.util.Map<String, com.citymodeler.matsim.models.osm.model.OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("W", SignalReadyFixtures.node("W", 0, 100));
        nodes.put("Wm", SignalReadyFixtures.node("Wm", 50, 100));
        nodes.put("N", SignalReadyFixtures.node("N", 100, 100));
        nodes.put("Em", SignalReadyFixtures.node("Em", 150, 100));
        nodes.put("E", SignalReadyFixtures.node("E", 200, 100));
        nodes.put("Sm", SignalReadyFixtures.node("Sm", 100, 50));
        nodes.put("S", SignalReadyFixtures.node("S", 100, 0));
        java.util.Map<String, com.citymodeler.matsim.models.osm.model.OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", SignalReadyFixtures.way("10", List.of("W", "Wm", "N"), "highway", "residential"));
        ways.put("20", SignalReadyFixtures.way("20", List.of("N", "Em", "E"), "highway", "residential"));
        ways.put("30", SignalReadyFixtures.way("30", List.of("S", "Sm", "N"), "highway", "residential"));
        return SignalReadyFixtures.result(nodes, ways);
    }
}
