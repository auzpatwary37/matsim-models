package com.citymodeler.matsim.models.network.turnrestrictions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.io.MatsimModelException;

class DisallowedNextLinksTest {

    @Test
    void emptyHasNoEntries() {
        DisallowedNextLinks value = DisallowedNextLinks.empty();
        assertTrue(value.asMap().isEmpty());
        assertEquals("{}", value.toJson());
    }

    @Test
    void plusRecordsSingleModeSingleSequence() {
        DisallowedNextLinks value = DisallowedNextLinks.empty()
                .plus("car", List.of("a", "b"));
        assertTrue(value.isDisallowed("car", List.of("a", "b")));
        assertFalse(value.isDisallowed("truck", List.of("a", "b")));
        assertFalse(value.isDisallowed("car", List.of("b", "a")));
        assertFalse(value.isDisallowed("car", List.of("a")));
        assertEquals(Map.of("car", List.of(List.of("a", "b"))), value.asMap());
    }

    @Test
    void plusAccumulatesMultipleModesAndSequences() {
        DisallowedNextLinks value = DisallowedNextLinks.empty()
                .plus("bus", List.of("x"))
                .plus("car", List.of("a", "b"))
                .plus("car", List.of("c"));
        assertTrue(value.isDisallowed("bus", List.of("x")));
        assertTrue(value.isDisallowed("car", List.of("a", "b")));
        assertTrue(value.isDisallowed("car", List.of("c")));
        assertEquals(List.of(List.of("a", "b"), List.of("c")), value.asMap().get("car"));
    }

    @Test
    void plusDeduplicatesIdenticalSequences() {
        DisallowedNextLinks value = DisallowedNextLinks.empty()
                .plus("car", List.of("a", "b"))
                .plus("car", List.of("a", "b"));
        assertEquals(List.of(List.of("a", "b")), value.asMap().get("car"));
    }

    @Test
    void asMapIsUnmodifiable() {
        DisallowedNextLinks value = DisallowedNextLinks.empty().plus("car", List.of("a"));
        assertThrows(UnsupportedOperationException.class, () -> value.asMap().put("bus", List.of()));
        assertThrows(UnsupportedOperationException.class, () -> value.asMap().get("car").add(List.of("z")));
    }

    @Test
    void plusRejectsBlankModeNullSequenceAndEmptySequence() {
        assertThrows(IllegalArgumentException.class, () -> DisallowedNextLinks.empty().plus("", List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> DisallowedNextLinks.empty().plus("  ", List.of("a")));
        assertThrows(NullPointerException.class, () -> DisallowedNextLinks.empty().plus(null, List.of("a")));
        assertThrows(NullPointerException.class, () -> DisallowedNextLinks.empty().plus("car", null));
        assertThrows(IllegalArgumentException.class, () -> DisallowedNextLinks.empty().plus("car", List.of()));
        assertThrows(IllegalArgumentException.class, () -> DisallowedNextLinks.empty().plus("car", Arrays.asList("a", null)));
    }

    @Test
    void toJsonSortsModesAndPreservesSequenceInsertionOrder() {
        DisallowedNextLinks value = DisallowedNextLinks.empty()
                .plus("zebra", List.of("a", "b"))
                .plus("alpha", List.of("x", "y"))
                .plus("alpha", List.of("z"));
        assertEquals("""
                {"alpha":[["x","y"],["z"]],"zebra":[["a","b"]]}""", value.toJson());
    }

    @Test
    void fromJsonRoundTrips() {
        DisallowedNextLinks value = DisallowedNextLinks.empty()
                .plus("car", List.of("a", "b"))
                .plus("bus", List.of("c"));
        assertEquals(value, DisallowedNextLinks.fromJson(value.toJson()));
    }

    @Test
    void fromJsonAcceptsEmptyObject() {
        assertEquals(DisallowedNextLinks.empty(), DisallowedNextLinks.fromJson("{}"));
    }

    @Test
    void fromJsonRejectsMalformedValues() {
        for (String malformed : List.of(
                "", "   ", "null", "[]", "\"car\"", "123",
                "{\"car\":\"not-an-array\"}",
                "{\"car\":[\"a\"]}",
                "{\"car\":[[\"a\",\"b\"],\"junk\"]}",
                "{\"car\":[[],[\"a\"]]}",
                "{\"car\":[[\"\", \"a\"]]}",
                "{\"car\":{\"deep\":true}}",
                "{not json")) {
            assertThrows(MatsimModelException.class,
                    () -> DisallowedNextLinks.fromJson(malformed),
                    "expected failure for: " + malformed);
        }
    }

    @Test
    void equalContentsAreEqual() {
        DisallowedNextLinks first = DisallowedNextLinks.empty()
                .plus("car", List.of("a", "b"))
                .plus("bus", List.of("c"));
        DisallowedNextLinks second = DisallowedNextLinks.empty()
                .plus("bus", List.of("c"))
                .plus("car", List.of("a", "b"));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, first.plus("car", List.of("z")));
    }
}
