package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class OsmTurnLaneParserTest {

    private final OsmTurnLaneParser parser = new OsmTurnLaneParser();

    @Test
    void splitsCellsAndTokensInOrder() {
        List<OsmTurnLaneCell> cells = parser.parse("left|through;right|none");
        assertEquals(3, cells.size());
        assertEquals(List.of(LaneTurnClass.LEFT), cells.get(0).indications());
        assertEquals(List.of(LaneTurnClass.THROUGH, LaneTurnClass.RIGHT), cells.get(1).indications());
        assertTrue(cells.get(2).indications().isEmpty(), "'none' is not a movement class here");
        assertFalse(cells.get(2).empty());
    }

    @Test
    void recognisesEveryMovementToken() {
        assertEquals(LaneTurnClass.SHARP_LEFT, parser.parse("sharp_left").get(0).indications().get(0));
        assertEquals(LaneTurnClass.SLIGHT_LEFT, parser.parse("slight_left").get(0).indications().get(0));
        assertEquals(LaneTurnClass.SLIGHT_RIGHT, parser.parse("slight_right").get(0).indications().get(0));
        assertEquals(LaneTurnClass.SHARP_RIGHT, parser.parse("sharp_right").get(0).indications().get(0));
        assertEquals(LaneTurnClass.REVERSE, parser.parse("reverse").get(0).indications().get(0));
    }

    @Test
    void mergeTokensCarryAMergeDirectionNotAMovement() {
        OsmTurnLaneCell c = parser.parse("merge_to_left").get(0);
        assertEquals(LaneMerge.LEFT, c.merge());
        assertTrue(c.indications().isEmpty());
        assertEquals(LaneMerge.RIGHT, parser.parse("merge_to_right").get(0).merge());
    }

    @Test
    void emptyCellIsMarkedEmpty() {
        List<OsmTurnLaneCell> cells = parser.parse("|through");
        assertTrue(cells.get(0).empty());
        assertTrue(cells.get(0).indications().isEmpty());
    }

    @Test
    void unknownTokenIsPreservedNotGuessed() {
        OsmTurnLaneCell c = parser.parse("left_turn").get(0);
        assertTrue(c.indications().isEmpty());
        assertEquals(List.of("left_turn"), c.unsupportedTokens());
        assertEquals("left_turn", c.raw());
    }

    @Test
    void blankInputYieldsNoCells() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("  ").isEmpty());
    }
}
