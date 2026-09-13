package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * A route whose only path requires a turn that is disallowed for the route's mode must not be mapped
 * onto that restricted sequence. Instead the mapper must bridge the unreachable transition with an
 * explicit {@code pt_} artificial connector, and the restricted link sequence must never appear
 * verbatim in the emitted network route.
 */
class TurnRestrictionRoutingTest {

    private static final Id<Link> F = Id.create("F", Link.class);
    private static final Id<Link> P1 = Id.create("P1", Link.class);
    private static final Id<Link> D = Id.create("D", Link.class);

    private Network network() {
        Network net = new Network();
        Node n0 = new Node(Id.create("n0", Node.class), new Coord(0, 0));
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(100, 0));
        Node n2 = new Node(Id.create("n2", Node.class), new Coord(200, 0));
        Node n3 = new Node(Id.create("n3", Node.class), new Coord(300, 0));
        net.addNode(n0);
        net.addNode(n1);
        net.addNode(n2);
        net.addNode(n3);
        Link f = new Link(F, n0.getId(), n1.getId(), 100.0, 900.0, 13.9, 2.0, Set.of("bus", "car"));
        Link p1 = new Link(P1, n1.getId(), n2.getId(), 100.0, 900.0, 13.9, 2.0, Set.of("bus", "car"));
        // The only successor of P1 is D, and that turn is forbidden for buses.
        p1.getAttributes().putAttribute("disallowedNextLinks", "{\"bus\":[[\"D\"]]}");
        Link d = new Link(D, n2.getId(), n3.getId(), 100.0, 900.0, 13.9, 2.0, Set.of("bus", "car"));
        net.addLink(f);
        net.addLink(p1);
        net.addLink(d);
        net.postProcess();
        return net;
    }

    private TransitMappingResult map(String mode) {
        Network net = network();
        TransitSchedule schedule = new TransitSchedule();
        schedule.addStopFacility(new TransitStopFacility(
                Id.create("s1", TransitStopFacility.class), new Coord(50, 0), false));
        schedule.addStopFacility(new TransitStopFacility(
                Id.create("s2", TransitStopFacility.class), new Coord(250, 0), false));
        TransitLine line = new TransitLine(Id.create("line", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route", TransitRoute.class));
        route.setTransportMode(mode);
        route.addStop(new TransitRouteStop(Id.create("s1", TransitStopFacility.class), 0, 0, false));
        route.addStop(new TransitRouteStop(Id.create("s2", TransitStopFacility.class), 100, 100, false));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        return new TransitNetworkMapper(TransitMappingConfig.defaults(),
                new LinkSpatialIndex(net, 100.0)).map(schedule, net);
    }

    private TransitRoute mappedRoute(TransitMappingResult result) {
        return result.mappedSchedule().getTransitLines()
                .get(Id.create("line", TransitLine.class)).getRoutes()
                .get(Id.create("route", TransitRoute.class));
    }

    @Test
    void disallowedTurnForcesArtificialConnector() {
        TransitMappingResult result = map("bus");
        TransitRoute mapped = mappedRoute(result);
        List<Id<Link>> networkRoute = mapped.getNetworkRoute();
        assertNotNull(networkRoute, "the bus route must still map (via a connector)");

        assertTrue(networkRoute.stream().anyMatch(id -> id.toString().startsWith("pt_")),
                "restricted bus turn must be bridged by a pt_ artificial connector, was " + networkRoute);

        // The restricted sequence P1 -> D must not appear verbatim.
        assertRestrictedSequenceAbsent(networkRoute);

        // Continuity is preserved by the connector (checked against the returned mapped network).
        assertContinuous(networkRoute, result.mappedNetwork());
    }

    /** Control: the identical geometry without the bus restriction is mapped onto P1 -> D normally. */
    @Test
    void unrestrictedModeUsesRestrictedTurn() {
        TransitMappingResult result = map("car");
        List<Id<Link>> networkRoute = mappedRoute(result).getNetworkRoute();
        assertNotNull(networkRoute);
        assertFalse(networkRoute.stream().anyMatch(id -> id.toString().startsWith("pt_")),
                "car has no restriction and must not need a connector");
        assertEquals(List.of(F, P1, D), networkRoute);
    }

    private void assertContinuous(List<Id<Link>> networkRoute, Network mappedNetwork) {
        for (int i = 0; i + 1 < networkRoute.size(); i++) {
            Link a = mappedNetwork.getLinks().get(networkRoute.get(i));
            Link b = mappedNetwork.getLinks().get(networkRoute.get(i + 1));
            assertNotNull(a, "missing link " + networkRoute.get(i));
            assertNotNull(b, "missing link " + networkRoute.get(i + 1));
            assertEquals(a.getToNodeId(), b.getFromNodeId(),
                    "discontinuous at " + a.getId() + " -> " + b.getId());
        }
    }

    private void assertRestrictedSequenceAbsent(List<Id<Link>> route) {
        for (int i = 0; i + 1 < route.size(); i++) {
            assertFalse(route.get(i).equals(P1) && route.get(i + 1).equals(D),
                    "restricted sequence P1 -> D appeared in " + route);
        }
    }
}
