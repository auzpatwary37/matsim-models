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
                TransitMappingConfig.defaults(), spatialIndex, null);
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
                TransitMappingConfig.defaults(), spatialIndex, null);
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
                new CandidateScoreWeights(-1, 0, 0, 0, 0, 0, 0, 0).validate());
    }

    @Test
    void weightValidationRejectsAllZero() {
        assertThrows(IllegalArgumentException.class, () ->
                new CandidateScoreWeights(0, 0, 0, 0, 0, 0, 0, 0).validate());
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
}
