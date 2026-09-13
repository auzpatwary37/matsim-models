package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void consistentDirectionalTagsAreAuthoritativeWithinTheDeclaredTotal() {
        // lanes=4 = forward 3 + backward 1: consistent, each direction authoritative.
        OsmTagSet consistent = tags(Map.of("lanes", "4", "lanes:forward", "3", "lanes:backward", "1"));
        OsmLaneCount fwd = resolver.resolve(consistent, true, false, 1.0);
        OsmLaneCount bwd = resolver.resolve(consistent, false, false, 1.0);
        assertEquals(3, fwd.lanes());
        assertEquals(1, bwd.lanes());
        assertEquals(LaneConfidence.PRESENT, fwd.confidence());
        assertFalse(fwd.issueCodes().contains("inconsistent-lane-tags"));
        assertFalse(bwd.issueCodes().contains("inconsistent-lane-tags"));
        assertEquals(4.0, fwd.lanes() + bwd.lanes(), "no directed count exceeds the declared total");

        // lanes=5 = forward 2 + backward 2 + both_ways 1: consistent; both_ways stays provenance.
        OsmTagSet withBoth = tags(Map.of(
                "lanes", "5", "lanes:both_ways", "1", "lanes:forward", "2", "lanes:backward", "2"));
        OsmLaneCount fwdBoth = resolver.resolve(withBoth, true, false, 1.0);
        OsmLaneCount bwdBoth = resolver.resolve(withBoth, false, false, 1.0);
        assertEquals(2, fwdBoth.lanes());
        assertEquals(2, bwdBoth.lanes());
        assertEquals(1.0, fwdBoth.bothWays());
        assertEquals(LaneConfidence.PRESENT, fwdBoth.confidence());
        assertFalse(fwdBoth.issueCodes().contains("inconsistent-lane-tags"));
        assertFalse(bwdBoth.issueCodes().contains("inconsistent-lane-tags"));
    }

    @Test
    void explicitDirectionalTagsCannotExceedTheDeclaredTotal() {
        // lanes=2 but forward 3 + backward 1 = 4 > 2: inconsistent. The contradictory values must
        // NOT be used as physical counts (spec §1a step 1); fall back to the total-derived split.
        OsmTagSet t = tags(Map.of("lanes", "2", "lanes:forward", "3", "lanes:backward", "1"));
        OsmLaneCount fwd = resolver.resolve(t, true, false, 1.0);
        OsmLaneCount bwd = resolver.resolve(t, false, false, 1.0);
        assertTrue(fwd.issueCodes().contains("inconsistent-lane-tags"));
        assertTrue(bwd.issueCodes().contains("inconsistent-lane-tags"));
        // Physical counts fall back to T=2 -> 1 per direction; never 3/1.
        assertEquals(1, fwd.lanes());
        assertEquals(1, bwd.lanes());
        assertEquals(LaneConfidence.EVEN_SPLIT, fwd.confidence());
        assertEquals(2.0, fwd.lanes() + bwd.lanes(),
                "directed lanes must not exceed the declared physical total");
    }

    @Test
    void directionalTagPlusBothWaysCannotExceedTheDeclaredTotal() {
        // lanes=3, both_ways=1, forward=3 -> 3 + 1 = 4 > 3: inconsistent.
        OsmTagSet fwd = tags(Map.of("lanes", "3", "lanes:both_ways", "1", "lanes:forward", "3"));
        OsmLaneCount fwdResult = resolver.resolve(fwd, true, false, 1.0);
        assertTrue(fwdResult.issueCodes().contains("inconsistent-lane-tags"));
        assertFalse(fwdResult.lanes() > 3, "never emit more physical lanes than declared");
        assertEquals(LaneConfidence.EVEN_SPLIT, fwdResult.confidence(),
                "T = 3 - 1 = 2 -> even split fallback");

        // lanes=4, both_ways=1, backward=4 -> 4 + 1 = 5 > 4: inconsistent.
        OsmTagSet bwd = tags(Map.of("lanes", "4", "lanes:both_ways", "1", "lanes:backward", "4"));
        OsmLaneCount bwdResult = resolver.resolve(bwd, false, false, 1.0);
        assertTrue(bwdResult.issueCodes().contains("inconsistent-lane-tags"));
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
        // Spec §1e: lanes=3 -> each direction emits round(T/2)=2 lane objects so that 1 + 1 <= 3,
        // flagged undetermined-split; the pair is one lane object more than the physical total and
        // is a directional conservative representation, not a physical decomposition.
        OsmTagSet t = tags(Map.of("lanes", "3"));
        OsmLaneCount fwd = resolver.resolve(t, true, false, 1.0);
        OsmLaneCount bwd = resolver.resolve(t, false, false, 1.0);
        assertEquals(2, fwd.lanes(), "round(T/2) physical lane objects, never an invented per-direction count");
        assertEquals(2, bwd.lanes(), "both directions get round(T/2) lane objects");
        assertEquals(LaneConfidence.UNDETERMINED_SPLIT, fwd.confidence());
        assertEquals(LaneConfidence.UNDETERMINED_SPLIT, bwd.confidence());
        assertEquals(3.0, fwd.undeterminedTotal());
        assertEquals(3.0, bwd.undeterminedTotal(), "the declared physical total is preserved");
        assertTrue(fwd.issueCodes().contains("undetermined-lane-split"));
        assertTrue(bwd.issueCodes().contains("undetermined-lane-split"));
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
        assertFalse(resolver.resolve(fwdOnly, false, false, 1.0).issueCodes()
                .contains("inconsistent-lane-tags"));

        OsmTagSet bwdOnly = tags(Map.of("lanes", "4", "lanes:backward", "1"));
        assertEquals(1, resolver.resolve(bwdOnly, false, false, 1.0).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(bwdOnly, false, false, 1.0).confidence());
        assertEquals(3, resolver.resolve(bwdOnly, true, false, 1.0).lanes(),
                "missing direction = total - known - both_ways");
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(bwdOnly, true, false, 1.0).confidence());
        assertFalse(resolver.resolve(bwdOnly, true, false, 1.0).issueCodes()
                .contains("inconsistent-lane-tags"));
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
