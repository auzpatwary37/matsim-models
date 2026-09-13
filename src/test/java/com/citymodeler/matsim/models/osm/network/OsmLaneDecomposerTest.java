package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;

class OsmLaneDecomposerTest {

    private final OsmLaneDecomposer decomposer = new OsmLaneDecomposer();
    private final OsmTurnLaneParser parser = new OsmTurnLaneParser();

    /** Junction: inLink l_in; outgoing THROUGH=l_t, LEFT=l_l, RIGHT=l_r. */
    private final MovementTurnClassifier junction = (in, out) -> switch (out) {
        case "l_t" -> LaneTurnClass.THROUGH;
        case "l_l" -> LaneTurnClass.LEFT;
        case "l_r" -> LaneTurnClass.RIGHT;
        default -> LaneTurnClass.UNKNOWN;
    };

    private Lane lane(LaneDecomposition d, int index) {
        return d.assignment().getLanes().get(Id.create("l_in_l" + index, Lane.class));
    }

    @Test
    void leftAndThroughCellsMapToTheCorrectOutgoingLinks() {
        OsmLaneCount count = new OsmLaneCount(2, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left|through"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertEquals(2, d.assignment().getLanes().size());
        assertEquals(List.of("l_l"), lane(d, 0).getToLinkIds().stream().map(Object::toString).toList());
        assertEquals(List.of("l_t"), lane(d, 1).getToLinkIds().stream().map(Object::toString).toList());
        assertEquals(900.0, lane(d, 0).getCapacityVehiclesPerHour());
        assertEquals("false", lane(d, 0).getAttributes().getAttribute("osm:lane.capacity.shared"));
    }

    @Test
    void sharedCellServesSeveralMovementsAndIsFlaggedSharedNotDoubled() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left;through"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        Lane shared = lane(d, 0);
        assertEquals(2, shared.getToLinkIds().size());
        assertEquals(900.0, shared.getCapacityVehiclesPerHour(), "lane capacity stays physical");
        assertEquals("true", shared.getAttributes().getAttribute("osm:lane.capacity.shared"));
    }

    @Test
    void absentTurnEvidenceFallsBackToAllOutgoingWithAbsentConfidence() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.ABSENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, List.of(),
                List.of("l_t", "l_l", "l_r"), junction, 600.0);

        assertEquals(3, lane(d, 0).getToLinkIds().size(), "schema requires at least one toLink");
        assertEquals(LaneConfidence.ABSENT, lane(d, 0).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void emptyMiddleCellEmitsIssueAndFallsBackToAllOutgoingWithAbsentConfidence() {
        OsmLaneCount count = new OsmLaneCount(3, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left||right"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertTrue(d.issues().stream().anyMatch(i -> i.code().equals("empty-turn-cell")));
        assertEquals(3, lane(d, 1).getToLinkIds().size(), "empty cell -> all outgoing");
        assertEquals(LaneConfidence.ABSENT, lane(d, 1).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void unsupportedTokenEmitsIssueAndFallsBackToAllOutgoingWithUnsupportedConfidence() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left_turn"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertTrue(d.issues().stream().anyMatch(i -> i.code().equals("unsupported-turn-token")));
        assertEquals(3, lane(d, 0).getToLinkIds().size(), "unsupported token -> all outgoing");
        assertEquals(LaneConfidence.UNSUPPORTED, lane(d, 0).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void emptyOutgoingLinksRejected() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        assertThrows(IllegalArgumentException.class, () -> decomposer.decompose("l_in", count,
                parser.parse("through"), List.of(), junction, 900.0));
        assertThrows(IllegalArgumentException.class, () -> decomposer.decompose("l_in", count,
                parser.parse("through"), null, junction, 900.0));
        assertThrows(IllegalArgumentException.class, () -> decomposer.decompose("l_in", count,
                parser.parse("through"), java.util.Arrays.asList("l_t", null), junction, 900.0));
    }

    @Test
    void tokenCountMismatchAdoptsTurnLanesWhenLaneCountUndetermined() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.UNDETERMINED_SPLIT, 3.0, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left|through"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertEquals(2, d.assignment().getLanes().size());
        assertTrue(d.issues().stream().anyMatch(i -> i.code().equals("lane-count-mismatch")));
        assertEquals(LaneConfidence.TURN_LANES_AUTHORITATIVE,
                lane(d, 0).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void tokenCountMismatchKeepsLaneCountWhenKnownAndAlignsPositionally() {
        OsmLaneCount count = new OsmLaneCount(3, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left|through"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertEquals(3, d.assignment().getLanes().size());
        assertEquals(3, lane(d, 2).getToLinkIds().size(), "trailing lane has no evidence -> all outgoing");
        assertEquals(LaneConfidence.ABSENT, lane(d, 2).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void mergeCellEmitsSchemaLegalFallbackAndMergeAttribute() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("merge_to_left"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertNotNull(lane(d, 0));
        assertEquals("left", lane(d, 0).getAttributes().getAttribute("osm:lane.merge"));
        assertEquals(LaneConfidence.MERGE, lane(d, 0).getAttributes().getAttribute("osm:lane.confidence"));
        assertEquals(3, lane(d, 0).getToLinkIds().size());
    }

    @Test
    void noneCellIsNoneObservedAcrossAllOutgoing() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("none"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertEquals(3, lane(d, 0).getToLinkIds().size(), "none -> unrestricted lane");
        assertEquals(LaneConfidence.NONE_OBSERVED,
                lane(d, 0).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void parsedIndicationThatResolvesToNoMovementIsPartialNotAbsent() {
        // 'through' indication, but the junction has no through outgoing link.
        MovementTurnClassifier noThrough = (in, out) -> LaneTurnClass.LEFT;
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("through"),
                List.of("l_l", "l_r"), noThrough, 900.0);

        assertEquals(2, lane(d, 0).getToLinkIds().size(), "unresolved indication -> all outgoing");
        assertEquals(LaneConfidence.PARTIAL, lane(d, 0).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void baseTurnDoesNotFallBackToSlightMovement() {
        // Only a slight-left movement exists, but the lane says 'left': no direct left -> partial.
        MovementTurnClassifier slightOnly = (in, out) -> "l_sl".equals(out)
                ? LaneTurnClass.SLIGHT_LEFT : LaneTurnClass.THROUGH;
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left"),
                List.of("l_sl", "l_t"), slightOnly, 900.0);

        assertEquals(2, lane(d, 0).getToLinkIds().size(), "no base movement -> all outgoing");
        assertEquals(LaneConfidence.PARTIAL, lane(d, 0).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void sharpTurnStillFallsBackToBaseMovement() {
        // A 'sharp_left' lane with only a base left movement present resolves to it (spec §1b).
        MovementTurnClassifier baseOnly = (in, out) -> "l_l".equals(out)
                ? LaneTurnClass.LEFT : LaneTurnClass.THROUGH;
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("sharp_left"),
                List.of("l_l", "l_t"), baseOnly, 900.0);

        assertEquals(List.of("l_l"), lane(d, 0).getToLinkIds().stream().map(Object::toString).toList());
    }

    @Test
    void sourceCountProvenanceAndLaneIndexAreEmitted() {
        OsmLaneCount count = new OsmLaneCount(2, LaneConfidence.EVEN_SPLIT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left|through"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertEquals(2, lane(d, 0).getAttributes().getAttribute("osm:lanes.count"));
        assertEquals(2, lane(d, 0).getAttributes().getAttribute("osm:turnLanes.count"));
        assertEquals(0, lane(d, 0).getAttributes().getAttribute("osm:lane.index"));
        assertEquals(1, lane(d, 1).getAttributes().getAttribute("osm:lane.index"));
    }

    @Test
    void laneIdsAreDeterministicLeftToRight() {
        OsmLaneCount count = new OsmLaneCount(2, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("abc", count, parser.parse("left|right"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);
        assertTrue(d.assignment().getLanes().containsKey(Id.create("abc_l0", Lane.class)));
        assertTrue(d.assignment().getLanes().containsKey(Id.create("abc_l1", Lane.class)));
    }
}
