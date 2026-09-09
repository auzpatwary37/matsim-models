package com.citymodeler.matsim.models.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class MatsimIdsTest {

    @Test
    void composeJoinsPartsWithDelimiter() {
        assertEquals("a:b", MatsimIds.compose(":", "a", "b"));
        assertEquals("10.link:pt_10", MatsimIds.compose(".", "10", "link:pt_10"));
    }

    @Test
    void escapeDoublesDelimiterOccurrencesInsideParts() {
        assertEquals("a::b", MatsimIds.escape("a:b", ":"));
        assertEquals("plain", MatsimIds.escape("plain", ":"));
        assertEquals("a::b::c::d", MatsimIds.escape("a:b:c:d", ":"));
    }

    @Test
    void composeEscapesAndRoundTrips() {
        String id = MatsimIds.compose(":", "a:b", "c");
        assertEquals("a::b:c", id);
        assertEquals(List.of("a:b", "c"), MatsimIds.decompose(id, ":"));
    }

    @Test
    void roundTripsPartEndingWithDelimiter() {
        String id = MatsimIds.compose(":", "a", "b:");
        assertEquals("a:b::", id);
        assertEquals(List.of("a", "b:"), MatsimIds.decompose(id, ":"));
    }

    @Test
    void composeRejectsNullAndBlankParts() {
        assertThrows(NullPointerException.class, () -> MatsimIds.compose(":", null, "b"));
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.compose(":", "a", ""));
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.compose(":", "a", "   "));
    }

    @Test
    void composeRejectsInvalidDelimiters() {
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.compose("", "a"));
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.compose("::", "a"));
    }

    @Test
    void decomposeRejectsBlankIdsAndTrailingDelimiters() {
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.decompose("", ":"));
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.decompose("   ", ":"));
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.decompose("a:", ":"));
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.decompose(":a", ":"));
    }

    @Test
    void decomposeRejectsInvalidDelimiters() {
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.decompose("a:b", ""));
        assertThrows(IllegalArgumentException.class, () -> MatsimIds.decompose("a:b", "::"));
    }
}
