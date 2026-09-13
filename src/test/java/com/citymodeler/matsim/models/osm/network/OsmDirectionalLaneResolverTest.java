package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.osm.OsmTagSet;

class OsmDirectionalLaneResolverTest {

    private final OsmDirectionalLaneResolver resolver = new OsmDirectionalLaneResolver();

    private static OsmTagSet tags(Map<String, String> t) {
        return OsmTagSet.of(t);
    }

    @Test
    void explicitDirectionalTagIsAuthoritative() {
        OsmTagSet t = tags(Map.of("lanes", "2", "lanes:forward", "3", "lanes:backward", "1"));
        assertEquals(3, resolver.resolve(t, true, false).lanes());
        assertEquals(1, resolver.resolve(t, false, false).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(t, true, false).confidence());
    }

    @Test
    void onewayUsesTotalForTravelledDirection() {
        OsmTagSet t = tags(Map.of("highway", "primary", "oneway", "yes", "lanes", "3"));
        assertEquals(3, resolver.resolve(t, true, true).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(t, true, true).confidence());
    }

    @Test
    void bidirectionalEvenTotalSplitsEvenly() {
        assertEquals(1, resolver.resolve(tags(Map.of("lanes", "2")), true, false).lanes());
        assertEquals(2, resolver.resolve(tags(Map.of("lanes", "4")), true, false).lanes());
        assertEquals(2, resolver.resolve(tags(Map.of("lanes", "4")), false, false).lanes());
        assertEquals(LaneConfidence.EVEN_SPLIT,
                resolver.resolve(tags(Map.of("lanes", "4")), true, false).confidence());
    }

    @Test
    void bidirectionalOddTotalIsUndeterminedNotFabricated() {
        OsmLaneCount c = resolver.resolve(tags(Map.of("lanes", "3")), true, false);
        assertEquals(1, c.lanes(), "undetermined split keeps a single undivided lane");
        assertEquals(LaneConfidence.UNDETERMINED_SPLIT, c.confidence());
        assertEquals(3.0, c.undeterminedTotal());
        assertTrue(c.issueCodes().contains("undetermined-lane-split"));
    }

    @Test
    void bothWaysIsExcludedFromTheSplitAndCountedForBothDirections() {
        // 5 total - 1 center = 4 directional -> 2 per direction, plus the shared lane.
        OsmLaneCount fwd = resolver.resolve(
                tags(Map.of("lanes", "5", "lanes:both_ways", "1")), true, false);
        assertEquals(3, fwd.lanes(), "2 through + 1 both_ways");
        assertEquals(2, fwd.lanes() - 1);
        assertEquals(LaneConfidence.EVEN_SPLIT, fwd.confidence());
    }

    @Test
    void loneDirectionalTagIsHonoredForItsDirection() {
        OsmTagSet fwdOnly = tags(Map.of("lanes", "4", "lanes:forward", "3"));
        assertEquals(3, resolver.resolve(fwdOnly, true, false).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(fwdOnly, true, false).confidence());
        assertEquals(2, resolver.resolve(fwdOnly, false, false).lanes(),
                "opposite direction falls back to the total split");
        assertEquals(LaneConfidence.EVEN_SPLIT, resolver.resolve(fwdOnly, false, false).confidence());

        OsmTagSet bwdOnly = tags(Map.of("lanes", "4", "lanes:backward", "1"));
        assertEquals(1, resolver.resolve(bwdOnly, false, false).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(bwdOnly, false, false).confidence());
        assertEquals(2, resolver.resolve(bwdOnly, true, false).lanes(),
                "opposite direction falls back to the total split");
    }

    @Test
    void malformedCountsAreIgnoredWithIssue() {
        for (String bad : List.of("0", "-1", "1.5", "none", " ", "", "NaN", "Infinity", "-Infinity")) {
            OsmLaneCount c = resolver.resolve(tags(Map.of("lanes", bad)), true, false);
            assertEquals(LaneConfidence.ABSENT, c.confidence(), "bad value: '" + bad + "'");
            assertEquals(1, c.lanes(), "bad value: '" + bad + "'");
            assertTrue(c.issueCodes().contains("malformed-lane-count"), "bad value: '" + bad + "'");
            assertNull(c.undeterminedTotal(), "bad value: '" + bad + "'");
        }
    }

    @Test
    void malformedDirectionalTagReportsSingleIssue() {
        OsmLaneCount c = resolver.resolve(tags(Map.of("lanes:forward", "none")), true, false);
        assertEquals(1, c.lanes());
        assertEquals(LaneConfidence.ABSENT, c.confidence());
        assertEquals(1, c.issueCodes().stream()
                .filter("malformed-lane-count"::equals).count(),
                "a malformed tag must be reported exactly once");
    }

    @Test
    void noLaneTagsGivesOneAbsentLane() {
        OsmLaneCount c = resolver.resolve(tags(Map.of("highway", "residential")), true, false);
        assertEquals(1, c.lanes());
        assertEquals(LaneConfidence.ABSENT, c.confidence());
    }
}
