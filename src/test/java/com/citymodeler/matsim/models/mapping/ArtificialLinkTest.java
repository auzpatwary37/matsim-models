package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Coverage for {@link ArtificialLinkFactory}: artificial links are always usable network edges
 * (positive length/capacity/freespeed/lanes) and are tagged both by a {@code pt_} id prefix and the
 * {@code artificial=true} attribute. Mapping the same schedule twice with different insertion orders
 * must produce the same number of artificial links (the mapping is order-independent).
 */
class ArtificialLinkTest {

    @Test
    void createLoopProducesUsableTaggedLink() {
        Network net = new Network();
        net.postProcess();
        ArtificialLinkFactory factory = new ArtificialLinkFactory(net);

        Link loop = factory.createLoop(new Coord(10, 20), "bus", "ctx");
        assertNotNull(loop);
        assertTrue(loop.getLength() >= 1.0, "loop length must be >= 1, was " + loop.getLength());
        assertTrue(loop.getCapacity() > 0, "loop capacity must be positive");
        assertTrue(loop.getFreespeed() > 0, "loop freespeed must be positive");
        assertTrue(loop.getNumberOfLanes() >= 1, "loop must have at least one lane");
        assertEquals("true", loop.getAttributes().getAttribute("artificial"));
        assertTrue(loop.getId().toString().startsWith("pt_"), "artificial id must be pt_-prefixed");
        assertNotNull(net.getLinks().get(loop.getId()), "loop must be added to the network");
    }

    @Test
    void createConnectorProducesUsableTaggedLink() {
        Network net = new Network();
        Node a = new Node(Id.create("a", Node.class), new Coord(0, 0));
        Node b = new Node(Id.create("b", Node.class), new Coord(100, 0));
        net.addNode(a);
        net.addNode(b);
        net.postProcess();
        ArtificialLinkFactory factory = new ArtificialLinkFactory(net);

        Link connector = factory.createConnector(a, b, "bus", "ctx");
        assertTrue(connector.getLength() >= 1.0);
        assertTrue(connector.getCapacity() > 0);
        assertTrue(connector.getFreespeed() > 0);
        assertTrue(connector.getNumberOfLanes() >= 1);
        assertEquals("true", connector.getAttributes().getAttribute("artificial"));
        assertTrue(connector.getId().toString().startsWith("pt_"));
        assertEquals(a.getId(), connector.getFromNodeId());
        assertEquals(b.getId(), connector.getToNodeId());
    }

    /** Mapping is order-independent: shuffling facility/line insertion order keeps the count stable. */
    @Test
    void artificialLinkCountIsStableAcrossInsertionOrder() {
        int forward = artificialLinkCount(false);
        int shuffled = artificialLinkCount(true);
        assertEquals(forward, shuffled,
                "artificial link count must not depend on schedule insertion order");
        assertTrue(forward > 0, "the isolated stops must force artificial links");
    }

    private int artificialLinkCount(boolean shuffled) {
        Network net = new Network();
        Node n0 = new Node(Id.create("n0", Node.class), new Coord(0, 0));
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(100, 0));
        net.addNode(n0);
        net.addNode(n1);
        net.addLink(new Link(Id.create("l0", Link.class), n0.getId(), n1.getId(),
                100.0, 900.0, 13.9, 2.0, Set.of("bus", "car")));
        net.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        // Two stops far from any link -> both require artificial loops.
        TransitStopFacility sa = new TransitStopFacility(
                Id.create("a", TransitStopFacility.class), new Coord(100000, 100000), false);
        TransitStopFacility sb = new TransitStopFacility(
                Id.create("b", TransitStopFacility.class), new Coord(200000, 200000), false);
        if (shuffled) {
            schedule.addStopFacility(sb);
            schedule.addStopFacility(sa);
        } else {
            schedule.addStopFacility(sa);
            schedule.addStopFacility(sb);
        }

        TransitLine line1 = new TransitLine(Id.create("line1", TransitLine.class));
        TransitRoute route1 = new TransitRoute(Id.create("route1", TransitRoute.class));
        route1.setTransportMode("bus");
        route1.addStop(new TransitRouteStop(Id.create("a", TransitStopFacility.class), 0, 0, false));
        route1.addStop(new TransitRouteStop(Id.create("b", TransitStopFacility.class), 60, 60, false));
        line1.addRoute(route1);

        TransitLine line2 = new TransitLine(Id.create("line2", TransitLine.class));
        TransitRoute route2 = new TransitRoute(Id.create("route2", TransitRoute.class));
        route2.setTransportMode("bus");
        route2.addStop(new TransitRouteStop(Id.create("b", TransitStopFacility.class), 0, 0, false));
        route2.addStop(new TransitRouteStop(Id.create("a", TransitStopFacility.class), 60, 60, false));
        line2.addRoute(route2);

        if (shuffled) {
            schedule.addTransitLine(line2);
            schedule.addTransitLine(line1);
        } else {
            schedule.addTransitLine(line1);
            schedule.addTransitLine(line2);
        }
        schedule.postProcess();

        TransitMappingResult result = new TransitNetworkMapper(TransitMappingConfig.defaults(),
                new LinkSpatialIndex(net, 100.0)).map(schedule, net);

        List<String> artificialIds = new ArrayList<>();
        for (Id<Link> id : result.mappedNetwork().getLinks().keySet()) {
            if (id.toString().startsWith("pt_")) {
                artificialIds.add(id.toString());
            }
        }
        assertTrue(result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("loop")),
                "isolated stops should warn about created loops");
        return artificialIds.size();
    }
}
