package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportResult;

final class JunctionSignalDescriptorTest {

    private static OsmSimplifiedNetwork simplifyCrossroads() {
        OsmImportResult r = SignalReadyFixtures.crossroads();
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        return OsmSignalAwareSimplifier.simplify(mat, r, cfg, OsmSimplifyOptions.defaults());
    }

    private static OsmSimplifiedNetwork simplify(OsmImportResult r, OsmSimplifyOptions o) {
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        return OsmSignalAwareSimplifier.simplify(mat, r, cfg, o);
    }

    private static Optional<JunctionSignalDescriptor> find(OsmSimplifiedNetwork s, String osmId) {
        return Optional.ofNullable(s.junctionAt("osm_node_" + osmId));
    }

    @Test
    void detectsExactlyOneSignalizedJunctionAtSignalizedNode() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        assertEquals(1, s.signalizedJunctions().size());
        JunctionSignalDescriptor j = find(s, "N").orElseThrow();
        assertEquals("signal_N", j.junctionId());
        assertEquals("N", j.primaryOsmNodeId());
        assertTrue(j.confirmedSignalized());
        // Fixture node uses only highway=traffic_signals (presence), no traffic_signals value.
        assertEquals(2, j.signalConfidence());
    }

    @Test
    void enumeratesIncomingAndOutgoingLinks() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        JunctionSignalDescriptor j = find(s, "N").orElseThrow();
        assertEquals(List.of("sim_10_r_N_W", "sim_20_f_E_N", "sim_30_r_N_S"), j.incomingLinks());
        assertEquals(List.of("sim_10_f_N_W", "sim_20_r_E_N", "sim_30_f_N_S"), j.outgoingLinks());
    }

    @Test
    void enumeratesMovementsAndTurnTypes() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        JunctionSignalDescriptor j = find(s, "N").orElseThrow();
        // 3 incoming x 3 outgoing = 9 turning movements (incoming/outgoing are disjoint).
        assertEquals(9, j.movements().size());
        assertTrue(j.hasMovement("sim_10_r_N_W", "sim_20_r_E_N"));
        assertFalse(j.hasMovement("sim_10_r_N_W", "sim_10_r_N_W"));

        assertEquals(OsmTurnType.THROUGH, turn(j, "sim_10_r_N_W", "sim_20_r_E_N"));
        assertEquals(OsmTurnType.RIGHT, turn(j, "sim_10_r_N_W", "sim_30_f_N_S"));
        assertEquals(OsmTurnType.U_TURN, turn(j, "sim_10_r_N_W", "sim_10_f_N_W"));
        assertEquals(OsmTurnType.RIGHT, turn(j, "sim_30_r_N_S", "sim_20_r_E_N"));
        assertEquals(OsmTurnType.LEFT, turn(j, "sim_30_r_N_S", "sim_10_f_N_W"));
    }

    @Test
    void restrictionMakesMovementFullyRestricted() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        JunctionSignalDescriptor j = find(s, "N").orElseThrow();
        SignalizedMovement m = movement(j, "sim_10_r_N_W", "sim_20_r_E_N");
        assertNotNull(m);
        assertTrue(m.fullyRestricted());
        assertTrue(m.restrictedModes().contains("car"));
        assertEquals(1, j.prohibitedMovementCount());
        assertEquals(8, j.legalMovementCount());
    }

    /** A legal approach is not falsely flagged even when one of its movements is car-restricted. */
    @Test
    void legalApproachIsNotFlaggedDespiteRestrictedMovement() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        boolean flagged = s.issues().stream()
                .anyMatch(i -> "approach-no-legal-outgoing".equals(i.code()));
        assertFalse(flagged);
    }

    /**
     * Review BLOCKER: two distinct signalized intersections joined only by a normal street must NOT
     * be merged, even when they sit within the (old) distance threshold.
     */
    @Test
    void twoSeparateIntersectionsAreNotMerged() {
        OsmSimplifiedNetwork def =
                simplify(SignalReadyFixtures.twoSeparateIntersections(), OsmSimplifyOptions.defaults());
        assertEquals(2, def.signalizedJunctions().size());

        OsmSimplifiedNetwork agg =
                simplify(SignalReadyFixtures.twoSeparateIntersections(),
                        new OsmSimplifyOptions(30.0, false, 35.0, Set.of()));
        assertEquals(2, agg.signalizedJunctions().size());
        assertNotNull(agg.junctionAt("osm_node_S1"));
        assertNotNull(agg.junctionAt("osm_node_S2"));
        assertNotSame(agg.junctionAt("osm_node_S1"), agg.junctionAt("osm_node_S2"));
    }

    /**
     * Review BLOCKER: a transitive A-B-C chain (A-C beyond the threshold) must not glue the far
     * intersections together via the middle one.
     */
    @Test
    void threeSignalsInARowDoNotChainingTransitively() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.threeSignalsInARow(),
                        new OsmSimplifyOptions(60.0, false, 35.0, Set.of()));
        assertEquals(3, s.signalizedJunctions().size());
        assertNotSame(s.junctionAt("osm_node_A"), s.junctionAt("osm_node_B"));
        assertNotSame(s.junctionAt("osm_node_A"), s.junctionAt("osm_node_C"));
    }

    /**
     * Review positive: the two signal corners of a genuinely wide intersection, joined by a
     * {@code highway=link} internal road, form ONE junction, resolvable from either member.
     */
    @Test
    void wideIntersectionClustersIntoOneJunctionAndMembersResolve() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.oneWideIntersection(),
                        new OsmSimplifyOptions(40.0, false, 35.0, Set.of()));
        assertEquals(1, s.signalizedJunctions().size());
        JunctionSignalDescriptor jA = s.junctionAt("osm_node_A");
        JunctionSignalDescriptor jB = s.junctionAt("osm_node_B");
        assertNotNull(jA);
        assertSame(jA, jB);
        assertTrue(jA.osmNodeIds().contains("A"));
        assertTrue(jA.osmNodeIds().contains("B"));
        assertEquals("signal_A", jA.junctionId());
        assertTrue(jA.confirmedSignalized());
    }

    /**
     * Review BLOCKER/MAJOR: a cross-node movement is only emitted when the departure corner is
     * internally reachable, and its turn type is UNKNOWN (the geometry angle would be meaningless).
     */
    @Test
    void crossNodeMovementIsReachabilityGatedAndTurnTypeUnknown() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.oneWideIntersection(),
                        new OsmSimplifyOptions(40.0, false, 35.0, Set.of()));
        JunctionSignalDescriptor j = s.junctionAt("osm_node_A");
        // Arrives at A, departs at B: reachable through the internal link, so present but UNKNOWN.
        assertTrue(j.hasMovement("sim_10_r_A_an", "sim_12_f_B_bs"));
        assertEquals(OsmTurnType.UNKNOWN, turn(j, "sim_10_r_A_an", "sim_12_f_B_bs"));
        // Arrives and departs at A: a real, geometry-derived turn type.
        assertTrue(j.hasMovement("sim_10_r_A_an", "sim_11_f_A_aw"));
        assertNotEquals(OsmTurnType.UNKNOWN, turn(j, "sim_10_r_A_an", "sim_11_f_A_aw"));
    }

    /** Review BLOCKER: a one-way internal link removes the directionally-impossible reverse movement. */
    @Test
    void onewayInternalLinkFiltersImpossibleReverseMovement() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.oneWideIntersectionOneway(),
                        new OsmSimplifyOptions(40.0, false, 35.0, Set.of()));
        JunctionSignalDescriptor j = s.junctionAt("osm_node_A");
        // A -> B reachable (the one-way internal link direction).
        assertTrue(j.hasMovement("sim_10_r_A_an", "sim_12_f_B_bs"));
        // B -> A is not reachable: the phantom reverse movement must be absent.
        assertFalse(j.hasMovement("sim_12_r_B_bs", "sim_11_f_A_aw"));
    }

    /**
     * Review #1: OSM represents ramp/connector roads as the parent class with a {@code _link} suffix.
     * The classifier must recognize the real taxonomy (and still accept bare {@code link}), and must
     * not treat ordinary classes as link roads.
     */
    @Test
    void isLinkHighwayRecognizesRealLinkClasses() {
        assertTrue(OsmSignalAwareSimplifier.isLinkHighway("motorway_link"));
        assertTrue(OsmSignalAwareSimplifier.isLinkHighway("trunk_link"));
        assertTrue(OsmSignalAwareSimplifier.isLinkHighway("primary_link"));
        assertTrue(OsmSignalAwareSimplifier.isLinkHighway("secondary_link"));
        assertTrue(OsmSignalAwareSimplifier.isLinkHighway("tertiary_link"));
        assertTrue(OsmSignalAwareSimplifier.isLinkHighway("link"));
        assertFalse(OsmSignalAwareSimplifier.isLinkHighway("motorway"));
        assertFalse(OsmSignalAwareSimplifier.isLinkHighway("primary"));
        assertFalse(OsmSignalAwareSimplifier.isLinkHighway("residential"));
        assertFalse(OsmSignalAwareSimplifier.isLinkHighway(null));
    }

    /**
     * Review #1 integration: a wide intersection whose internal road is a real {@code primary_link}
     * (not the invented {@code highway=link}) still enters the network and clusters into one junction.
     */
    @Test
    void realPrimaryLinkRoadDrivesClustering() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.wideIntersectionPrimaryLink(),
                        new OsmSimplifyOptions(40.0, false, 35.0, Set.of()));
        assertEquals(1, s.signalizedJunctions().size());
        assertSame(s.junctionAt("osm_node_A"), s.junctionAt("osm_node_B"));
    }

    /**
     * Review #2: two signal corners joined only through a NON-signal internal node X. The cross-way
     * contraction engine dissolves the degree-2 X into a single merged A->B internal link (that is
     * its purpose), and the A-to-B cross-node movement must still be emitted through it.
     */
    @Test
    void viaInternalNodeClusterEmitsCrossNodeMovement() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.wideIntersectionViaInternalNode(),
                        new OsmSimplifyOptions(40.0, false, 35.0, Set.of()));
        assertEquals(1, s.signalizedJunctions().size());
        // The non-signal internal node X is contracted into the merged A->B link.
        assertFalse(s.network().getNodes().containsKey(com.citymodeler.matsim.models.api.Id
                .create("osm_node_X", com.citymodeler.matsim.models.network.Node.class)));
        assertTrue(s.collapsedLink("sim_90_f_A_B").segmentCount() == 2);
        JunctionSignalDescriptor j = s.junctionAt("osm_node_A");
        assertNotNull(j);
        // Bidirectional internal path: both cross-node directions are reachable.
        assertTrue(j.hasMovement("sim_10_r_A_an", "sim_12_f_B_bs"));
        assertTrue(j.hasMovement("sim_12_r_B_bs", "sim_11_f_A_aw"));
    }

    /** Review #2: with a one-way internal path A -> X -> B, the impossible reverse movement is absent. */
    @Test
    void viaInternalNodeOnewayFiltersImpossibleReverseMovement() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.wideIntersectionViaInternalNodeOneway(),
                        new OsmSimplifyOptions(40.0, false, 35.0, Set.of()));
        JunctionSignalDescriptor j = s.junctionAt("osm_node_A");
        assertNotNull(j);
        assertTrue(j.hasMovement("sim_10_r_A_an", "sim_12_f_B_bs"));
        assertFalse(j.hasMovement("sim_12_r_B_bs", "sim_11_f_A_aw"));
    }

    /**
     * Review #3: junction membership is direction-independent. The only internal connectivity is a
     * one-way B -> X -> A path even though {@code A} sorts before {@code B}; the pair must still
     * cluster. Movement direction, however, stays directional (A -> B is absent).
     */
    @Test
    void clusteringMembershipIsDirectionIndependent() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.wideIntersectionOnewayAgainstSort(),
                        new OsmSimplifyOptions(40.0, false, 35.0, Set.of()));
        assertEquals(1, s.signalizedJunctions().size(),
                "one-way-against-sort internal path must still form one junction");
        assertSame(s.junctionAt("osm_node_A"), s.junctionAt("osm_node_B"));
        JunctionSignalDescriptor j = s.junctionAt("osm_node_A");
        // B -> A is the only reachable cross-node direction.
        assertTrue(j.hasMovement("sim_12_r_B_bs", "sim_11_f_A_aw"));
        assertFalse(j.hasMovement("sim_10_r_A_an", "sim_12_f_B_bs"));
    }

    /**
     * Review BLOCKER: a transitive A-B-C chain joined by junction-internal {@code *_link} roads
     * where adjacent pairs qualify within the threshold but A-C does not. A whole-cluster check must
     * never glue A-C together (the old pairwise union-find would).
     */
    @Test
    void threeLinkedSignalsDoNotChainTransitively() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.threeLinkedSignalsTransitiveChain(),
                        new OsmSimplifyOptions(60.0, false, 35.0, Set.of()));
        JunctionSignalDescriptor jA = s.junctionAt("osm_node_A");
        JunctionSignalDescriptor jC = s.junctionAt("osm_node_C");
        assertNotNull(jA);
        assertNotNull(jC);
        assertNotSame(jA, jC, "A and C must not share a junction: A-C does not qualify");
        assertFalse(jA.osmNodeIds().contains("C"), "A's junction must not contain C");
    }

    /**
     * Review #2: movement reachability must be restricted to the witness paths that justified the
     * cluster, not an unconstrained global BFS. Without witnesses, cross-member movement is absent.
     */
    @Test
    void witnessReachabilityIsRestrictedToAcceptedPaths() {
        boolean[][] none = OsmSignalAwareSimplifier.witnessReachability(
                List.of("A", "B"), List.of());
        assertTrue(none[0][0]);
        assertFalse(none[0][1], "no witness path => A cannot reach B");
        assertFalse(none[1][0]);

        boolean[][] viaX = OsmSignalAwareSimplifier.witnessReachability(
                List.of("A", "B"), List.of(List.of("osm_node_A", "osm_node_X", "osm_node_B")));
        assertTrue(viaX[0][1], "accepted witness A->X->B gives A->B");
        assertFalse(viaX[1][0], "direction is preserved: B cannot reach A");
    }

    /**
     * Review #4: internal junction-box connector links must not be advertised as signal approaches
     * or departures.
     */
    @Test
    void internalJunctionLinksAreNotApproachesOrDepartures() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.wideIntersectionViaInternalNode(),
                        new OsmSimplifyOptions(40.0, false, 35.0, Set.of()));
        JunctionSignalDescriptor j = s.junctionAt("osm_node_A");
        assertNotNull(j);
        assertFalse(j.internalLinks().isEmpty(), "internal A->X/X->B connectors must be recorded");
        for (String internal : j.internalLinks()) {
            assertFalse(j.incomingLinks().contains(internal),
                    "internal link exposed as incoming: " + internal);
            assertFalse(j.outgoingLinks().contains(internal),
                    "internal link exposed as outgoing: " + internal);
        }
    }

    /**
     * Review #1: with three mutually-qualifying members A, B, C, the direct A->C witness must be
     * preserved (not discarded as a non-spanning-tree edge). Arriving at A and departing at C's arm
     * must be a valid cross-node movement.
     */
    @Test
    void allMemberWitnessesArePreservedInTriangleCluster() {
        OsmSimplifiedNetwork s =
                simplify(SignalReadyFixtures.wideIntersectionTriangle(),
                        new OsmSimplifyOptions(60.0, false, 35.0, Set.of()));
        JunctionSignalDescriptor j = s.junctionAt("osm_node_A");
        assertNotNull(j);
        assertTrue(j.osmNodeIds().containsAll(List.of("A", "B", "C")),
                "A, B, C all pairwise qualify => one cluster");
        // Arrive at A (from an), depart at C (to ce): direct A->C witness must survive.
        assertTrue(j.hasMovement("sim_10_r_A_an", "sim_13_f_C_ce"),
                "direct A->C witness must not be lost to spanning-tree bookkeeping");
    }

    /**
     * Review #2: the shortest internal path must respect BOTH distance and hop budgets. The lower-
     * distance A->B route burns all hops; a slightly longer fewer-hop route must still be found.
     */
    @Test
    void hopConstrainedInternalPathFindsFeasibleRoute() {
        OsmImportResult r = SignalReadyFixtures.hopConstrainedInternalPath();
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        Network net = mat.cleanedNetwork();

        Map<String, List<String>> adj = OsmSignalAwareSimplifier.internalAdjacency(net, r.ways());
        // Directed lengths over the internal (*_link) subgraph.
        Map<String, Double> len = new java.util.HashMap<>();
        for (Link link : net.getLinks().values()) {
            len.merge(link.getFromNode().getId() + "|" + link.getToNode().getId(), link.getLength(),
                    Math::min);
        }

        // 3-hop budget: the 5-hop p-chain is infeasible, the 2-hop q-route must be returned.
        java.util.List<String> path = OsmSignalAwareSimplifier.shortestInternalPath(
                "osm_node_A", "osm_node_B", Set.of("osm_node_A", "osm_node_B"), adj, len, 1000.0, 3);
        assertNotNull(path, "a feasible <=3-hop path exists via q");
        assertTrue(path.contains("osm_node_q"), "the fewer-hop route must be selected, got " + path);
        assertFalse(path.contains("osm_node_p3"), "the hop-heavy p-chain is infeasible");
    }

    private static SignalizedMovement movement(JunctionSignalDescriptor j, String in, String out) {
        return j.movements().stream()
                .filter(m -> m.incomingLinkId().equals(in) && m.outgoingLinkId().equals(out))
                .findFirst().orElse(null);
    }

    private static OsmTurnType turn(JunctionSignalDescriptor j, String in, String out) {
        SignalizedMovement m = movement(j, in, out);
        assertNotNull(m);
        return m.turnType();
    }
}
