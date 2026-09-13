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
        assertEquals(3, resolver.resolve(t, true, false, 1.0).lanes());
        assertEquals(1, resolver.resolve(t, false, false, 1.0).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(t, true, false, 1.0).confidence());
    }

    @Test
    void onewayUsesTotalForTravelledDirection() {
        OsmTagSet t = tags(Map.of("highway", "primary", "oneway", "yes", "lanes", "3"));
        assertEquals(3, resolver.resolve(t, true, true, 1.0).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(t, true, true, 1.0).confidence());
    }

    @Test
    void bidirectionalEvenTotalSplitsEvenly() {
        assertEquals(1, resolver.resolve(tags(Map.of("lanes", "2")), true, false, 1.0).lanes());
        assertEquals(2, resolver.resolve(tags(Map.of("lanes", "4")), true, false, 1.0).lanes());
        assertEquals(2, resolver.resolve(tags(Map.of("lanes", "4")), false, false, 1.0).lanes());
        assertEquals(LaneConfidence.EVEN_SPLIT,
                resolver.resolve(tags(Map.of("lanes", "4")), true, false, 1.0).confidence());
    }

    @Test
    void bidirectionalOddTotalIsUndeterminedNotFabricated() {
        OsmLaneCount c = resolver.resolve(tags(Map.of("lanes", "3")), true, false, 1.0);
        assertEquals(2, c.lanes(), "round(T/2) physical lane objects, never an invented per-direction count");
        assertEquals(LaneConfidence.UNDETERMINED_SPLIT, c.confidence());
        assertEquals(3.0, c.undeterminedTotal());
        assertTrue(c.issueCodes().contains("undetermined-lane-split"));
    }

    @Test
    void bothWaysIsProvenanceOnlyAndExcludedFromTheSplit() {
        // Spec §1a step 4: lanes:both_ways is provenance only; it is NOT duplicated into each
        // direction. 5 total - 1 center = 4 directional -> 2 per direction.
        OsmLaneCount fwd = resolver.resolve(
                tags(Map.of("lanes", "5", "lanes:both_ways", "1")), true, false, 1.0);
        OsmLaneCount bwd = resolver.resolve(
                tags(Map.of("lanes", "5", "lanes:both_ways", "1")), false, false, 1.0);
        assertEquals(2, fwd.lanes(), "both_ways is not added to the forward direction");
        assertEquals(2, bwd.lanes(), "both_ways is not added to the backward direction");
        assertEquals(LaneConfidence.EVEN_SPLIT, fwd.confidence());
        // Physical sum invariant: forward + backward + bothWays == declared lanes.
        assertEquals(1.0, fwd.bothWays());
        assertEquals(5.0, fwd.lanes() + bwd.lanes() + fwd.bothWays());
    }

    @Test
    void loneDirectionalTagIsHonoredAndMissingDirectionIsDerived() {
        OsmTagSet fwdOnly = tags(Map.of("lanes", "4", "lanes:forward", "3"));
        assertEquals(3, resolver.resolve(fwdOnly, true, false, 1.0).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(fwdOnly, true, false, 1.0).confidence());
        assertEquals(1, resolver.resolve(fwdOnly, false, false, 1.0).lanes(),
                "missing direction = total - known - both_ways");
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(fwdOnly, false, false, 1.0).confidence());

        OsmTagSet bwdOnly = tags(Map.of("lanes", "4", "lanes:backward", "1"));
        assertEquals(1, resolver.resolve(bwdOnly, false, false, 1.0).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(bwdOnly, false, false, 1.0).confidence());
        assertEquals(3, resolver.resolve(bwdOnly, true, false, 1.0).lanes(),
                "missing direction = total - known - both_ways");
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(bwdOnly, true, false, 1.0).confidence());
    }

    @Test
    void inconsistentLoneDirectionalTagFallsBackWithIssue() {
        // lanes=2 but lanes:forward=3 leaves other = 2 - 3 = -1 < 1 -> inconsistent.
        OsmLaneCount c = resolver.resolve(tags(Map.of("lanes", "2", "lanes:forward", "3")),
                false, false, 1.0);
        assertTrue(c.issueCodes().contains("inconsistent-lane-tags"));
        // Falls back to the total-split handling: even T=2 -> 1 per direction.
        assertEquals(1, c.lanes());
        assertEquals(LaneConfidence.EVEN_SPLIT, c.confidence());
    }

    @Test
    void malformedCountsAreIgnoredWithIssue() {
        for (String bad : List.of("0", "-1", "1.5", "none", " ", "", "NaN", "Infinity", "-Infinity")) {
            OsmLaneCount c = resolver.resolve(tags(Map.of("lanes", bad)), true, false, 1.0);
            assertEquals(LaneConfidence.ABSENT, c.confidence(), "bad value: '" + bad + "'");
            assertEquals(1, c.lanes(), "bad value: '" + bad + "'");
            assertTrue(c.issueCodes().contains("malformed-lane-count"), "bad value: '" + bad + "'");
            assertNull(c.undeterminedTotal(), "bad value: '" + bad + "'");
        }
    }

    @Test
    void malformedDirectionalTagReportsSingleIssue() {
        OsmLaneCount c = resolver.resolve(tags(Map.of("lanes:forward", "none")), true, false, 1.0);
        assertEquals(1, c.lanes());
        assertEquals(LaneConfidence.ABSENT, c.confidence());
        assertEquals(1, c.issueCodes().stream()
                .filter("malformed-lane-count"::equals).count(),
                "a malformed tag must be reported exactly once");
    }

    @Test
    void noLaneTagsGivesRuleDefaultAbsentLanes() {
        // Spec §1a step 5: absent tags fall back to the network's own per-direction count, so
        // laneDefinitions agrees with network.xml permlanes.
        OsmLaneCount c = resolver.resolve(tags(Map.of("highway", "motorway")), true, false, 3.0);
        assertEquals(3, c.lanes());
        assertEquals(LaneConfidence.ABSENT, c.confidence());

        OsmLaneCount residential = resolver.resolve(
                tags(Map.of("highway", "residential")), true, false, 1.0);
        assertEquals(1, residential.lanes());
        assertEquals(LaneConfidence.ABSENT, residential.confidence());
    }
}
