package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

class TransitNetworkMapperTest {

    private Network network;
    private LinkSpatialIndex spatialIndex;

    @BeforeEach
    void setUp() {
        network = new Network();
        // Simple two-node network: A -- B
        Node a = new Node(Id.create("A", Node.class), new Coord(0, 0));
        Node b = new Node(Id.create("B", Node.class), new Coord(100, 0));
        network.addNode(a);
        network.addNode(b);
        Link l1 = new Link(Id.create("l1", Link.class), a.getId(), b.getId(),
                100.0, 900.0, 13.9, 2.0, Set.of("car", "pt"));
        network.addLink(l1);
        network.postProcess();
        spatialIndex = new LinkSpatialIndex(network, 100.0);
    }

    @Test
    void mapsStopToNearbyLink() {
        // Create a stop near link l1
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop = new TransitStopFacility(
                Id.create("stop1", TransitStopFacility.class), new Coord(50, 5), false);
        stop.setName("Test Stop");
        schedule.addStopFacility(stop);

        TransitLine line = new TransitLine(Id.create("line1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route1", TransitRoute.class));
        route.setTransportMode("pt");
        route.addStop(new TransitRouteStop(Id.create("stop1", TransitStopFacility.class), 0, 0, false));
        route.addDeparture(new Departure(Id.create("dep1", Departure.class), 0));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        TransitNetworkMapper mapper = new TransitNetworkMapper(
                TransitMappingConfig.defaults(), spatialIndex);
        TransitMappingResult result = mapper.map(schedule, network);

        // Stop should be assigned to link l1
        assertEquals(Id.create("l1", Link.class), stop.getLinkId());
        // Route should have network route
        assertNotNull(route.getNetworkRoute());
        assertEquals(1, route.getNetworkRoute().size());
    }

    @Test
    void createsLoopForIsolatedStop() {
        // Stop far from any link
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop = new TransitStopFacility(
                Id.create("far_stop", TransitStopFacility.class), new Coord(100000, 100000), false);
        schedule.addStopFacility(stop);

        TransitLine line = new TransitLine(Id.create("line1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route1", TransitRoute.class));
        route.setTransportMode("pt");
        route.addStop(new TransitRouteStop(Id.create("far_stop", TransitStopFacility.class), 0, 0, false));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        TransitNetworkMapper mapper = new TransitNetworkMapper(
                TransitMappingConfig.defaults(), spatialIndex);
        TransitMappingResult result = mapper.map(schedule, network);

        // Should have created an artificial loop
        assertNotNull(stop.getLinkId());
        assertTrue(stop.getLinkId().toString().startsWith("pt_loop"));
        assertTrue(result.hasWarnings());
    }

    @Test
    void childStopCreatedWithCorrectId() {
        TransitStopFacility parent = new TransitStopFacility(
                Id.create("parent1", TransitStopFacility.class), new Coord(0, 0), false);
        Id<Link> linkId = Id.create("l1", Link.class);

        String childId = ChildStopCreator.childStopId("parent1", "l1");
        assertEquals("parent1.link:l1", childId);

        // Dots in parent are escaped
        String childId2 = ChildStopCreator.childStopId("a.b", "l1");
        assertEquals("a..b.link:l1", childId2);
    }

    @Test
    void weightValidationRejectsNegatives() {
        assertThrows(IllegalArgumentException.class, () ->
                new CandidateScoreWeights(-1, 0).validate());
    }

    @Test
    void weightValidationRejectsAllZero() {
        assertThrows(IllegalArgumentException.class, () ->
                new CandidateScoreWeights(0, 0).validate());
    }

    @Test
    void artificialLinkHasValidFields() {
        ArtificialLinkFactory factory = new ArtificialLinkFactory(network);
        Node a = network.getNodes().get(Id.create("A", Node.class));
        Node b = network.getNodes().get(Id.create("B", Node.class));
        Link link = factory.createConnector(a, b, "pt", "test");

        assertTrue(link.getLength() > 0);
        assertTrue(link.getFreespeed() > 0);
        assertTrue(link.getCapacity() > 0);
        assertTrue(link.getNumberOfLanes() > 0);
        assertTrue(link.getId().toString().startsWith("pt_"));
    }

    // ---- Review regressions ----

    /**
     * Review #1: two distinct WGS84 stops are projected into the network's shared CRS and must snap
     * to two DISTINCT links (~20 km apart), not collapse onto the same nearest link.
     */
    @Test
    void twoWgs84StopsProjectAndSnapToDistinctLinks() {
        Network net = new Network();
        CrsUtils.Projector proj = CrsUtils.forCrs("EPSG:3857");
        Coord pa = proj.project(-73.560, 45.500);
        Coord pb = proj.project(-73.400, 45.600);
        Node na = new Node(Id.create("na", Node.class), pa);
        Node na2 = new Node(Id.create("na2", Node.class), new Coord(pa.getX() + 800, pa.getY()));
        Node nb = new Node(Id.create("nb", Node.class), pb);
        Node nb2 = new Node(Id.create("nb2", Node.class), new Coord(pb.getX() - 800, pb.getY()));
        net.addNode(na);
        net.addNode(na2);
        net.addNode(nb);
        net.addNode(nb2);
        net.addLink(new Link(Id.create("la", Link.class), na.getId(), na2.getId(), 800, 900, 13.9, 2.0, Set.of("car", "pt")));
        net.addLink(new Link(Id.create("lb", Link.class), nb.getId(), nb2.getId(), 800, 900, 13.9, 2.0, Set.of("car", "pt")));
        net.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility f1 = wgs84Stop("w1", -73.560, 45.500);
        TransitStopFacility f2 = wgs84Stop("w2", -73.400, 45.600);
        schedule.addStopFacility(f1);
        schedule.addStopFacility(f2);
        TransitLine line = new TransitLine(Id.create("ln", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("rt", TransitRoute.class));
        route.setTransportMode("pt");
        route.addStop(new TransitRouteStop(Id.create("w1", TransitStopFacility.class), 0, 0, false));
        route.addStop(new TransitRouteStop(Id.create("w2", TransitStopFacility.class), 100, 100, false));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        TransitNetworkMapper mapper = new TransitNetworkMapper(
                TransitMappingConfig.defaults(), new LinkSpatialIndex(net, 500.0));
        mapper.map(schedule, net);

        assertEquals(Id.create("la", Link.class), f1.getLinkId());
        assertEquals(Id.create("lb", Link.class), f2.getLinkId());
        assertNotEquals(f1.getLinkId(), f2.getLinkId());
    }

    /**
     * Review #2: after mapping, the route's stops are rewired to per-(parent, link) child facilities
     * instead of referencing the shared parent facility directly.
     */
    @Test
    void routeStopsRewiredToChildFacilities() {
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop = new TransitStopFacility(
                Id.create("stop1", TransitStopFacility.class), new Coord(50, 5), false);
        schedule.addStopFacility(stop);
        TransitLine line = new TransitLine(Id.create("line1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route1", TransitRoute.class));
        route.setTransportMode("pt");
        route.addStop(new TransitRouteStop(Id.create("stop1", TransitStopFacility.class), 0, 0, false));
        route.addDeparture(new Departure(Id.create("dep1", Departure.class), 0));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        TransitNetworkMapper mapper = new TransitNetworkMapper(
                TransitMappingConfig.defaults(), spatialIndex);
        mapper.map(schedule, network);

        TransitRouteStop routedStop = route.getStops().get(0);
        String childId = routedStop.getStopFacilityId().toString();
        assertNotEquals("stop1", childId, "route stop must reference a child, not the parent");
        assertEquals("stop1.link:l1", childId);
        assertNotNull(schedule.getFacilities().get(routedStop.getStopFacilityId()),
                "the child facility must have been materialized");
    }

    /**
     * Review #13: when there is no path between two mapped stop links the mapper inserts an explicit
     * artificial connector — it never emits a raw concatenation of unrelated links.
     */
    @Test
    void disjointComponentsAreBridgedWithExplicitConnector() {
        Network net = new Network();
        // Component A
        Node a0 = new Node(Id.create("a0", Node.class), new Coord(0, 0));
        Node a1 = new Node(Id.create("a1", Node.class), new Coord(100, 0));
        // Component B (disjoint, far away)
        Node b0 = new Node(Id.create("b0", Node.class), new Coord(5000, 0));
        Node b1 = new Node(Id.create("b1", Node.class), new Coord(5100, 0));
        net.addNode(a0);
        net.addNode(a1);
        net.addNode(b0);
        net.addNode(b1);
        net.addLink(new Link(Id.create("la", Link.class), a0.getId(), a1.getId(), 100, 900, 13.9, 2.0, Set.of("car", "pt")));
        net.addLink(new Link(Id.create("lb", Link.class), b0.getId(), b1.getId(), 100, 900, 13.9, 2.0, Set.of("car", "pt")));
        net.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility f1 = new TransitStopFacility(
                Id.create("s1", TransitStopFacility.class), new Coord(0, 0), false);
        TransitStopFacility f2 = new TransitStopFacility(
                Id.create("s2", TransitStopFacility.class), new Coord(5000, 0), false);
        schedule.addStopFacility(f1);
        schedule.addStopFacility(f2);
        TransitLine line = new TransitLine(Id.create("ln", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("rt", TransitRoute.class));
        route.setTransportMode("pt");
        route.addStop(new TransitRouteStop(Id.create("s1", TransitStopFacility.class), 0, 0, false));
        route.addStop(new TransitRouteStop(Id.create("s2", TransitStopFacility.class), 100, 100, false));
        route.addDeparture(new Departure(Id.create("dep", Departure.class), 0));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        TransitNetworkMapper mapper = new TransitNetworkMapper(
                TransitMappingConfig.defaults(), new LinkSpatialIndex(net, 500.0));
        TransitMappingResult result = mapper.map(schedule, net);

        // The route is kept continuous via an explicit connector, not a raw [la, lb] concatenation.
        assertNotNull(route.getNetworkRoute());
        boolean hasConnector = route.getNetworkRoute().stream()
                .anyMatch(id -> id.toString().startsWith("pt_"));
        assertTrue(hasConnector, "expected an artificial connector in " + route.getNetworkRoute());
        assertTrue(result.warnings().stream()
                .anyMatch(w -> w.toLowerCase().contains("connector")));
    }

    private TransitStopFacility wgs84Stop(String id, double lon, double lat) {
        TransitStopFacility f = new TransitStopFacility(Id.create(id, TransitStopFacility.class),
                new Coord(0, 0), false);
        f.getAttributes().putAttribute("gtfs:lon", lon);
        f.getAttributes().putAttribute("gtfs:lat", lat);
        return f;
    }

    /**
     * Regression: an unmapped WGS84 stop gets an artificial loop, and that loop's node must be in the
     * NETWORK CRS (projected meters), never the raw WGS84 degrees. A degrees-valued loop node would
     * sit ~6,000 km from the network it is added to.
     */
    @Test
    void artificialLoopUsesProjectedNetworkCrsNotRawWgs84() {
        Network net = new Network();
        net.getAttributes().putAttribute("osm:targetCrs", "EPSG:3857");
        // A network far from (0,0) so a raw-degree coordinate is obviously wrong.
        Node a = new Node(Id.create("a", Node.class), new Coord(682000, 6376000));
        Node b = new Node(Id.create("b", Node.class), new Coord(682100, 6376000));
        net.addNode(a);
        net.addNode(b);
        net.addLink(new Link(Id.create("l1", Link.class), a.getId(), b.getId(),
                100.0, 900.0, 13.9, 2.0, Set.of("car", "pt")));
        net.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        // Luxembourg City WGS84; no link anywhere near -> artificial loop.
        TransitStopFacility stop = wgs84Stop("s1", 6.13, 49.61);
        schedule.addStopFacility(stop);
        TransitLine line = new TransitLine(Id.create("line1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route1", TransitRoute.class));
        route.setTransportMode("pt");
        route.addStop(new TransitRouteStop(Id.create("s1", TransitStopFacility.class), 0, 0, false));
        line.addRoute(route);
        schedule.addTransitLine(line);
        schedule.postProcess();

        // Tiny candidate radius so the distant stop definitely gets a loop.
        TransitMappingConfig cfg = new TransitMappingConfig(1.0, 1.0, 1, 1000.0,
                CandidateScoreWeights.defaults(), java.util.Map.of());
        TransitMappingResult result = new TransitNetworkMapper(cfg, new LinkSpatialIndex(net, 100.0))
                .map(schedule, net);

        Node loopNode = result.mappedNetwork().getNodes().values().stream()
                .filter(n -> n.getId().toString().startsWith("pt_loop_node_"))
                .findFirst().orElseThrow(() -> new AssertionError("expected an artificial loop node"));
        Coord c = loopNode.getCoord();
        // Projected Luxembourg is ~x=682,000 / y=6,376,000 m; raw degrees would be ~6.13 / 49.61.
        assertTrue(c.getX() > 1000 && c.getX() < 1_000_000,
                "loop node x must be in projected meters, was " + c.getX());
        assertTrue(c.getY() > 1_000_000,
                "loop node y must be in projected meters, was " + c.getY());
    }
}
