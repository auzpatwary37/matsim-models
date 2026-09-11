package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Review: legality predicates must distinguish fully / partially / fully-restricted movements. */
final class SignalizedMovementTest {

    @Test
    void fullyLegalMovement() {
        SignalizedMovement m = new SignalizedMovement(
                "in", "out", OsmTurnType.THROUGH, Set.of("car", "bus"), Set.of());
        assertTrue(m.fullyLegal());
        assertTrue(m.anyLegal());
        assertFalse(m.fullyRestricted());
        assertEquals(Set.of("car", "bus"), m.legalModes());
    }

    @Test
    void partiallyRestrictedMovementStillHasLegalModes() {
        // Car prohibited, bus legal: a usable approach for bus, not a free or fully-blocked movement.
        SignalizedMovement m = new SignalizedMovement(
                "in", "out", OsmTurnType.LEFT, Set.of("car", "bus"), Set.of("car"));
        assertEquals(Set.of("bus"), m.legalModes());
        assertTrue(m.anyLegal());
        assertFalse(m.fullyLegal());
        assertFalse(m.fullyRestricted());
    }

    @Test
    void fullyRestrictedMovementHasNoLegalModes() {
        SignalizedMovement m = new SignalizedMovement(
                "in", "out", OsmTurnType.RIGHT, Set.of("car"), Set.of("car"));
        assertTrue(m.fullyRestricted());
        assertFalse(m.anyLegal());
        assertFalse(m.fullyLegal());
        assertTrue(m.legalModes().isEmpty());
    }
}
