package com.citymodeler.matsim.models.network.index;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Network;

class LinkSpatialIndexTest {

    private Network simpleGridNetwork() {
        Network net = new Network();
        net.createNode("n1", 0, 0);
        net.createNode("n2", 100, 0);
        net.createNode("n3", 0, 100);
        net.createLink("l1", "n1", "n2", 100, 1000, 13.9, 1, Set.of("car"));
        net.createLink("l2", "n1", "n3", 100, 1000, 13.9, 1, Set.of("car"));
        net.postProcess();
        return net;
    }

    @Test
    void emptyNetworkDoesNotThrow() {
        Network net = new Network();
        net.postProcess();
        assertDoesNotThrow(() -> new LinkSpatialIndex(net, 50.0));
    }

    @Test
    void findsNearbyLinks() {
        Network net = simpleGridNetwork();
        LinkSpatialIndex idx = new LinkSpatialIndex(net, 50.0);

        List<NearestLink> results = idx.nearestLinks(new Coord(50, 0), 5, 100.0);
        assertFalse(results.isEmpty());
        assertEquals("l1", results.get(0).linkId());
    }

    @Test
    void noResultsBeyondMaxDistance() {
        Network net = simpleGridNetwork();
        LinkSpatialIndex idx = new LinkSpatialIndex(net, 50.0);

        List<NearestLink> results = idx.nearestLinks(new Coord(10000, 0), 5, 10.0);
        assertTrue(results.isEmpty());
    }

    @Test
    void rejectsNonPositiveCellSize() {
        Network net = simpleGridNetwork();
        assertThrows(IllegalArgumentException.class, () -> new LinkSpatialIndex(net, 0));
        assertThrows(IllegalArgumentException.class, () -> new LinkSpatialIndex(net, -1));
    }
}
