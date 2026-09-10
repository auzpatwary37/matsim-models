package com.citymodeler.matsim.models.network.index;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Network;

class NodeSpatialIndexTest {

    private Network twoNodeNetwork() {
        Network net = new Network();
        net.createNode("n1", 0, 0);
        net.createNode("n2", 200, 0);
        net.createLink("l1", "n1", "n2", 200, 1000, 13.9, 1, Set.of("car"));
        net.postProcess();
        return net;
    }

    @Test
    void emptyNetworkDoesNotThrow() {
        Network net = new Network();
        net.postProcess();
        assertDoesNotThrow(() -> new NodeSpatialIndex(net, 50.0));
    }

    @Test
    void findsNearestNode() {
        Network net = twoNodeNetwork();
        NodeSpatialIndex idx = new NodeSpatialIndex(net, 50.0);

        List<NearestNode> results = idx.nearestNodes(new Coord(10, 0), 5, 100.0);
        assertFalse(results.isEmpty());
        assertEquals("n1", results.get(0).nodeId());
    }

    @Test
    void respectsMaxDistance() {
        Network net = twoNodeNetwork();
        NodeSpatialIndex idx = new NodeSpatialIndex(net, 50.0);

        List<NearestNode> results = idx.nearestNodes(new Coord(10000, 0), 5, 10.0);
        assertTrue(results.isEmpty());
    }

    @Test
    void rejectsNonPositiveCellSize() {
        Network net = twoNodeNetwork();
        assertThrows(IllegalArgumentException.class, () -> new NodeSpatialIndex(net, 0));
    }
}
