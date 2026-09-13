package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import java.util.Set;

/**
 * After mapping, every {@link TransitRouteStop} of a mapped route must reference a per-(parent, link)
 * child facility rather than the shared parent: the stop id has the form {@code parent.link:linkId},
 * and the child facility exists with its {@code linkId} set and {@code gtfs:parentStopId} metadata.
 */
class MappedChildStopsTest {

    private Node node(Network net, String id, double x) {
        Node n = new Node(Id.create(id, Node.class), new Coord(x, 0));
        net.addNode(n);
        return n;
    }

    private void link(Network net, String id, Node from, Node to) {
        net.addLink(new Link(Id.create(id, Link.class), from.getId(), to.getId(),
                100.0, 900.0, 13.9, 2.0, Set.of("bus", "car")));
    }

    @Test
    void everyMappedStopReferencesExistingChildFacility() {
        Network net = new Network();
        Node n0 = node(net, "n0", 0);
        Node n1 = node(net, "n1", 100);
        Node n2 = node(net, "n2", 200);
        link(net, "l0", n0, n1);
        link(net, "l1", n1, n2);
        net.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility parentA = new TransitStopFacility(
                Id.create("parentA", TransitStopFacility.class), new Coord(50, 0), false);
        parentA.setName("Alpha");
        TransitStopFacility parentB = new TransitStopFacility(
                Id.create("parentB", TransitStopFacility.class), new Coord(150, 0), false);
        parentB.setName("Beta");
        schedule.addStopFacility(parentA);
        schedule.addStopFacility(parentB);

        TransitLine line = new TransitLine(Id.create("line", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route", TransitRoute.class));
        route.setTransportMode("bus");
        route.addStop(new TransitRouteStop(Id.create("parentA", TransitStopFacility.class), 0, 0, false));
        route.addStop(new TransitRouteStop(Id.create("parentB", TransitStopFacility.class), 60, 60, false));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        TransitMappingResult result = new TransitNetworkMapper(TransitMappingConfig.defaults(),
                new LinkSpatialIndex(net, 100.0)).map(schedule, net);

        TransitSchedule mappedSchedule = result.mappedSchedule();
        TransitRoute mappedRoute = mappedSchedule.getTransitLines()
                .get(Id.create("line", TransitLine.class)).getRoutes()
                .get(Id.create("route", TransitRoute.class));

        // Build-new: the input parents are never mutated; only the returned copy has link ids.
        assertNull(parentA.getLinkId(), "input parent must not be mutated by mapping");
        assertNull(parentB.getLinkId(), "input parent must not be mutated by mapping");

        assertEquals(2, mappedRoute.getStops().size());
        for (TransitRouteStop stop : mappedRoute.getStops()) {
            String childId = stop.getStopFacilityId().toString();
            assertTrue(childId.contains(".link:"),
                    "mapped stop must reference a child id of form parent.link:linkId, was " + childId);

            String parentId = childId.substring(0, childId.indexOf(".link:"));
            String linkId = childId.substring(childId.indexOf(".link:") + ".link:".length());

            TransitStopFacility child = mappedSchedule.getFacilities().get(stop.getStopFacilityId());
            assertNotNull(child, "child facility must be materialized for " + childId);
            assertEquals(Id.create(linkId, Link.class), child.getLinkId(),
                    "child linkId must match the id suffix");
            assertEquals(parentId, child.getAttributes().getAttribute("gtfs:parentStopId"),
                    "child must carry gtfs:parentStopId metadata");
        }
    }

    /** Dots in a parent id are escaped by doubling, and the child still resolves in the schedule. */
    @Test
    void parentIdWithDotsIsEscapedInChildId() {
        Network net = new Network();
        Node n0 = node(net, "n0", 0);
        Node n1 = node(net, "n1", 100);
        link(net, "l0", n0, n1);
        net.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        schedule.addStopFacility(new TransitStopFacility(
                Id.create("a.b", TransitStopFacility.class), new Coord(50, 0), false));
        TransitLine line = new TransitLine(Id.create("line", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route", TransitRoute.class));
        route.setTransportMode("bus");
        route.addStop(new TransitRouteStop(Id.create("a.b", TransitStopFacility.class), 0, 0, false));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        TransitMappingResult result = new TransitNetworkMapper(TransitMappingConfig.defaults(),
                new LinkSpatialIndex(net, 100.0)).map(schedule, net);
        TransitRoute mappedRoute = result.mappedSchedule().getTransitLines()
                .get(Id.create("line", TransitLine.class)).getRoutes()
                .get(Id.create("route", TransitRoute.class));

        TransitRouteStop stop = mappedRoute.getStops().get(0);
        assertEquals("a..b.link:l0", stop.getStopFacilityId().toString());
        TransitStopFacility child = result.mappedSchedule().getFacilities().get(stop.getStopFacilityId());
        assertNotNull(child);
        assertEquals("a.b", child.getAttributes().getAttribute("gtfs:parentStopId"));
    }
}
