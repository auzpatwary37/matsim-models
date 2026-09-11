package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.osm.OsmImportResult;

final class JunctionSignalDescriptorTest {

    private static OsmSimplifiedNetwork simplifyCrossroads() {
        OsmImportResult r = SignalReadyFixtures.crossroads();
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        return OsmSignalAwareSimplifier.simplify(mat, r, cfg, OsmSimplifyOptions.defaults());
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
        assertEquals(List.of("sim_10_f_W_N", "sim_20_r_E_N", "sim_30_f_S_N"), j.incomingLinks());
        assertEquals(List.of("sim_10_r_N_W", "sim_20_f_N_E", "sim_30_r_N_S"), j.outgoingLinks());
    }

    @Test
    void enumeratesMovementsAndTurnTypes() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        JunctionSignalDescriptor j = find(s, "N").orElseThrow();
        // 3 incoming x 3 outgoing = 9 turning movements (incoming/outgoing are disjoint).
        assertEquals(9, j.movements().size());
        assertTrue(j.hasMovement("sim_10_f_W_N", "sim_20_f_N_E"));
        assertFalse(j.hasMovement("sim_10_f_W_N", "sim_10_f_W_N"));

        assertEquals(OsmTurnType.THROUGH, turn(j, "sim_10_f_W_N", "sim_20_f_N_E"));
        assertEquals(OsmTurnType.RIGHT, turn(j, "sim_10_f_W_N", "sim_30_r_N_S"));
        assertEquals(OsmTurnType.U_TURN, turn(j, "sim_10_f_W_N", "sim_10_r_N_W"));
        assertEquals(OsmTurnType.RIGHT, turn(j, "sim_30_f_S_N", "sim_20_f_N_E"));
        assertEquals(OsmTurnType.LEFT, turn(j, "sim_30_f_S_N", "sim_10_r_N_W"));
    }

    @Test
    void restrictionMakesMovementFullyRestricted() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        JunctionSignalDescriptor j = find(s, "N").orElseThrow();
        SignalizedMovement m = movement(j, "sim_10_f_W_N", "sim_20_f_N_E");
        assertNotNull(m);
        assertTrue(m.fullyRestricted());
        assertTrue(m.restrictedModes().contains("car"));
        assertEquals(1, j.prohibitedMovementCount());
        assertEquals(8, j.legalMovementCount());
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
