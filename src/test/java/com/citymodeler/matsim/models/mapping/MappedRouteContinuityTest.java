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

import java.util.List;
import java.util.Set;

/**
 * Regression coverage for the mapped route's continuity: the emitted network route must be a walk of
 * links where each link's {@code toNode} is the next link's {@code fromNode}, with any physical gap
 * bridged by an explicit {@code pt_} artificial link. A discontinuous raw concatenation is a bug.
 */
class MappedRouteContinuityTest {

    private Node node(Network net, String id, double x, double y) {
        Node n = new Node(Id.create(id, Node.class), new Coord(x, y));
        net.addNode(n);
        return n;
    }

    private Link link(Network net, String id, Node from, Node to, double length) {
        Link l = new Link(Id.create(id, Link.class), from.getId(), to.getId(),
                length, 900.0, 13.9, 2.0, Set.of("bus", "car"));
        net.addLink(l);
        return l;
    }

    private TransitStopFacility stop(TransitSchedule schedule, String id, double x, double y) {
        TransitStopFacility f = new TransitStopFacility(
                Id.create(id, TransitStopFacility.class), new Coord(x, y), false);
        schedule.addStopFacility(f);
        return f;
    }

    /** Every consecutive pair of the mapped network route must be topologically connected. */
    @Test
    void chainOfStopsProducesContinuousRoute() {
        Network net = new Network();
        Node n0 = node(net, "n0", 0, 0);
        Node n1 = node(net, "n1", 100, 0);
        Node n2 = node(net, "n2", 200, 0);
        Node n3 = node(net, "n3", 300, 0);
        link(net, "l0", n0, n1, 100);
        link(net, "l1", n1, n2, 100);
        link(net, "l2", n2, n3, 100);
        net.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        stop(schedule, "s0", 10, 0);
        stop(schedule, "s1", 110, 0);
        stop(schedule, "s2", 210, 0);
        TransitLine line = new TransitLine(Id.create("line", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route", TransitRoute.class));
        route.setTransportMode("bus");
        route.addStop(new TransitRouteStop(Id.create("s0", TransitStopFacility.class), 0, 0, false));
        route.addStop(new TransitRouteStop(Id.create("s1", TransitStopFacility.class), 60, 60, false));
        route.addStop(new TransitRouteStop(Id.create("s2", TransitStopFacility.class), 120, 120, false));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        TransitMappingResult result = new TransitNetworkMapper(
                TransitMappingConfig.defaults(), new LinkSpatialIndex(net, 100.0)).map(schedule, net);
        TransitRoute mapped = result.mappedSchedule().getTransitLines()
                .get(Id.create("line", TransitLine.class)).getRoutes()
                .get(Id.create("route", TransitRoute.class));

        List<Id<Link>> networkRoute = mapped.getNetworkRoute();
        assertNotNull(networkRoute, "the chain must map to a continuous network route");
        assertEquals(List.of(Id.create("l0", Link.class), Id.create("l1", Link.class),
                Id.create("l2", Link.class)), networkRoute, "chain maps to its natural links");
        assertContinuous(networkRoute, result.mappedNetwork());
    }

    /**
     * A route spanning two disjoint network components must remain continuous: the emitted sequence
     * is topologically connected, and the bridging element is an explicit {@code pt_} artificial
     * link rather than a raw concatenation of unrelated links.
     */
    @Test
    void discontinuityAcrossComponentsIsBridgedByArtificialLink() {
        Network net = new Network();
        Node a0 = node(net, "a0", 0, 0);
        Node a1 = node(net, "a1", 100, 0);
        Node b0 = node(net, "b0", 5000, 0);
        Node b1 = node(net, "b1", 5100, 0);
        link(net, "la", a0, a1, 100);
        link(net, "lb", b0, b1, 100);
        net.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        stop(schedule, "s1", 10, 0);
        stop(schedule, "s2", 5010, 0);
        TransitLine line = new TransitLine(Id.create("line", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route", TransitRoute.class));
        route.setTransportMode("bus");
        route.addStop(new TransitRouteStop(Id.create("s1", TransitStopFacility.class), 0, 0, false));
        route.addStop(new TransitRouteStop(Id.create("s2", TransitStopFacility.class), 100, 100, false));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        TransitMappingResult result = new TransitNetworkMapper(
                TransitMappingConfig.defaults(), new LinkSpatialIndex(net, 500.0)).map(schedule, net);
        TransitRoute mapped = result.mappedSchedule().getTransitLines()
                .get(Id.create("line", TransitLine.class)).getRoutes()
                .get(Id.create("route", TransitRoute.class));

        List<Id<Link>> networkRoute = mapped.getNetworkRoute();
        assertNotNull(networkRoute, "the disjoint route must be bridged, not dropped");
        assertTrue(networkRoute.stream().anyMatch(id -> id.toString().startsWith("pt_")),
                "the gap must be bridged by a pt_ artificial link, was " + networkRoute);
        assertContinuous(networkRoute, result.mappedNetwork());

        // The raw concatenation [la, lb] has no topological connection and must never be emitted.
        assertTrue(!(networkRoute.size() == 2
                        && networkRoute.get(0).equals(Id.create("la", Link.class))
                        && networkRoute.get(1).equals(Id.create("lb", Link.class))),
                "must not emit a discontinuous [la, lb] concatenation");
    }

    private void assertContinuous(List<Id<Link>> networkRoute, Network mappedNetwork) {
        for (int i = 0; i + 1 < networkRoute.size(); i++) {
            Link a = mappedNetwork.getLinks().get(networkRoute.get(i));
            Link b = mappedNetwork.getLinks().get(networkRoute.get(i + 1));
            assertNotNull(a, "missing link " + networkRoute.get(i));
            assertNotNull(b, "missing link " + networkRoute.get(i + 1));
            assertEquals(a.getToNodeId(), b.getFromNodeId(),
                    "discontinuous route at " + a.getId() + " -> " + b.getId());
        }
    }
}
