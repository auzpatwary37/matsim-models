package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Network;

class GeometryTurnClassifierTest {

    @Test
    void classifiesStraightLeftAndRightFromGeometry() {
        Network network = new Network();
        network.createNode("c", 0, 0);
        network.createNode("north", 0, 100);
        network.createNode("east", 100, 0);
        network.createNode("west", -100, 0);
        network.createLink("in", "c", "c", 1, 900, 10, 1, java.util.Set.of("car"));
        network.createLink("straight", "c", "north", 100, 900, 10, 1, java.util.Set.of("car"));
        network.createLink("right", "c", "east", 100, 900, 10, 1, java.util.Set.of("car"));
        network.createLink("left", "c", "west", 100, 900, 10, 1, java.util.Set.of("car"));

        OsmGeometryStore geometry = new OsmGeometryStore(Map.of(
                "in", new OsmPolyline(List.of(new Coord(0, -100), new Coord(0, 0))),
                "straight", new OsmPolyline(List.of(new Coord(0, 0), new Coord(0, 100))),
                "right", new OsmPolyline(List.of(new Coord(0, 0), new Coord(100, 0))),
                "left", new OsmPolyline(List.of(new Coord(0, 0), new Coord(-100, 0)))));

        GeometryTurnClassifier classifier = new GeometryTurnClassifier(network, geometry);
        assertEquals(LaneTurnClass.THROUGH, classifier.classify("in", "straight"));
        assertEquals(LaneTurnClass.RIGHT, classifier.classify("in", "right"));
        assertEquals(LaneTurnClass.LEFT, classifier.classify("in", "left"));
    }

    @Test
    void zeroLengthSegmentIsUnknownNotThrough() {
        Network network = new Network();
        network.createNode("c", 0, 0);
        network.createNode("north", 0, 100);
        network.createLink("in", "c", "c", 1, 900, 10, 1, java.util.Set.of("car"));
        network.createLink("out", "c", "north", 100, 900, 10, 1, java.util.Set.of("car"));

        OsmGeometryStore geometry = new OsmGeometryStore(Map.of(
                "in", new OsmPolyline(List.of(new Coord(0, 0), new Coord(0, 0))),
                "out", new OsmPolyline(List.of(new Coord(0, 0), new Coord(0, 100)))));

        GeometryTurnClassifier classifier = new GeometryTurnClassifier(network, geometry);
        assertEquals(LaneTurnClass.UNKNOWN, classifier.classify("in", "out"));
    }
}
