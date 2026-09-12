package com.citymodeler.matsim.models.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * PR #18 review robustness items: malformed dense-node input must produce explicit diagnostics
 * rather than silently dropping data or throwing an incidental runtime exception. The two guards
 * are tested directly (no hand-built PBF needed).
 */
class OsmPbfMalformedTest {

    /** Mismatched id/lat/lon lengths are detected and reported. */
    @Test
    void denseArrayMismatchIsDetected() {
        assertNull(OsmPbfReader.denseArrayMismatch(3, 3, 3), "equal lengths are valid");
        String mismatch = OsmPbfReader.denseArrayMismatch(3, 2, 3);
        assertNotNull(mismatch, "a length mismatch must be reported");
        assertTrue(mismatch.contains("ids=3") && mismatch.contains("lats=2") && mismatch.contains("lons=3"),
                "diagnostic must name the offending lengths: " + mismatch);
    }

    /** A well-formed keys_vals run parses fully with no truncation diagnostic. */
    @Test
    void wellFormedTagStreamParsesWithoutDiagnostic() {
        // keys_vals: key1=val2, key3=val1, terminator 0
        List<Integer> kv = List.of(1, 2, 3, 1, 0);
        Map<String, String> tags = new LinkedHashMap<>();
        List<String> truncation = new ArrayList<>();
        int next = OsmPbfReader.parseDenseTags(kv, 0, tags, i -> "s" + i, truncation);

        assertTrue(truncation.isEmpty(), "no truncation for a well-formed stream");
        assertEquals(5, next, "consumes the whole stream including the terminator");
        assertEquals("s2", tags.get("s1"));
        assertEquals("s1", tags.get("s3"));
    }

    /** A lone trailing key (no value) is reported explicitly and does not over-run. */
    @Test
    void truncatedTagStreamIsReported() {
        List<Integer> kv = List.of(1); // key 1 with no value, no terminator
        Map<String, String> tags = new LinkedHashMap<>();
        List<String> truncation = new ArrayList<>();
        OsmPbfReader.parseDenseTags(kv, 0, tags, i -> "s" + i, truncation);

        assertEquals(1, truncation.size(), "truncation must be reported once");
        assertTrue(truncation.get(0).contains("key 1"), "diagnostic identifies the key: " + truncation);
        assertTrue(tags.isEmpty(), "no tag is invented from a truncated pair");
    }

    /** Multiple nodes share one keys_vals stream; a truncation must not corrupt later nodes. */
    @Test
    void truncatedStreamStopsDeterministically() {
        // node A: key1=val2, terminator 0 ; then a lone key 3 (truncated)
        List<Integer> kv = List.of(1, 2, 0, 3);
        Map<String, String> tagsA = new LinkedHashMap<>();
        List<String> truncA = new ArrayList<>();
        int afterA = OsmPbfReader.parseDenseTags(kv, 0, tagsA, i -> "s" + i, truncA);
        assertEquals(3, afterA);
        assertTrue(truncA.isEmpty());

        Map<String, String> tagsB = new LinkedHashMap<>();
        List<String> truncB = new ArrayList<>();
        OsmPbfReader.parseDenseTags(kv, afterA, tagsB, i -> "s" + i, truncB);
        assertEquals(1, truncB.size());
        assertTrue(tagsB.isEmpty());
    }
}
