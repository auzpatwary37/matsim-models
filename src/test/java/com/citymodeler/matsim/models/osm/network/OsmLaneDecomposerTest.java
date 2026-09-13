package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;

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
        assertTrue(lane(d, 0).getToLinkIds().size() >= 1);
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
