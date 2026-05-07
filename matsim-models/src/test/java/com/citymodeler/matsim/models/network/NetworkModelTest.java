package com.citymodeler.matsim.models.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;

class NetworkModelTest {
    @Test
    void postProcessWiresLinkNodesAndNodeLinkMaps() {
        Network network = new Network("test");
        Node fromNode = new Node(Id.create("n1", Node.class), new Coord(0, 0));
        Node toNode = new Node(Id.create("n2", Node.class), new Coord(100, 0));
        Link link = new Link(Id.createLinkId("l1"), fromNode.getId(), toNode.getId(), 100, 1000, 13.89, 1, Set.of("car", "bike"));

        network.addNode(fromNode);
        network.addNode(toNode);
        network.addLink(link);
        network.postProcess();

        assertSame(fromNode, link.getFromNode());
        assertSame(toNode, link.getToNode());
        assertSame(link, fromNode.getOutLinks().get(link.getId()));
        assertSame(link, toNode.getInLinks().get(link.getId()));
    }

    @Test
    void postProcessClearsStaleNodeLinkReferencesBeforeRebuilding() {
        Network network = new Network();
        Node fromNode = new Node(Id.create("n1", Node.class), new Coord(0, 0));
        Node toNode = new Node(Id.create("n2", Node.class), new Coord(100, 0));
        Link link = new Link(Id.createLinkId("l1"), fromNode.getId(), toNode.getId(), 100, 1000, 13.89, 1, Set.of("car"));

        network.addNode(fromNode);
        network.addNode(toNode);
        network.addLink(link);

        network.postProcess();
        network.postProcess();

        assertEquals(1, fromNode.getOutLinks().size());
        assertEquals(1, toNode.getInLinks().size());
    }

    @Test
    void postProcessThrowsWhenLinkReferencesMissingNode() {
        Network network = new Network();
        Id<Node> missingNodeId = Id.create("missing", Node.class);
        Link link = new Link(Id.createLinkId("l1"), Id.create("n1", Node.class), missingNodeId, 100, 1000, 13.89, 1, Set.of("car"));

        network.addNode(new Node(Id.create("n1", Node.class), new Coord(0, 0)));
        network.addLink(link);

        IllegalStateException exception = assertThrows(IllegalStateException.class, network::postProcess);
        assertTrue(exception.getMessage().contains("l1"));
        assertTrue(exception.getMessage().contains("missing"));
    }

    @Test
    void linkDefaultsAllowedModesToCarWhenUnset() {
        Link nullModesLink = new Link(Id.createLinkId("l1"), Id.create("n1", Node.class), Id.create("n2", Node.class), 100, 1000, 13.89, 1, null);
        Link emptyModesLink = new Link(Id.createLinkId("l2"), Id.create("n1", Node.class), Id.create("n2", Node.class), 100, 1000, 13.89, 1, Set.of());

        assertEquals(Set.of("car"), nullModesLink.getAllowedModes());
        assertEquals(Set.of("car"), emptyModesLink.getAllowedModes());
    }

    @Test
    void lanesAssignmentStoresLanesAndReferences() {
        Id<Link> linkId = Id.createLinkId("l1");
        Lane lane = new Lane(Id.create("lane1", Lane.class));
        lane.addToLinkId(Id.createLinkId("l2"));
        lane.addToLaneId(Id.create("lane2", Lane.class));
        lane.setCapacityVehiclesPerHour(600);
        lane.setStartsAtMeterFromLinkEnd(25);
        lane.setAlignment("right");

        LanesToLinkAssignment assignment = new LanesToLinkAssignment(linkId);
        assignment.addLane(lane);
        Lanes lanes = new Lanes();
        lanes.addAssignment(assignment);

        assertSame(assignment, lanes.getLanesToLinkAssignments().get(linkId));
        assertSame(lane, assignment.getLanes().get(lane.getId()));
        assertEquals(List.of(Id.createLinkId("l2")), lane.getToLinkIds());
        assertEquals(List.of(Id.create("lane2", Lane.class)), lane.getToLaneIds());
        assertEquals(600, lane.getCapacityVehiclesPerHour());
        assertEquals(25, lane.getStartsAtMeterFromLinkEnd());
        assertEquals("right", lane.getAlignment());
    }

    @Test
    void createLinkIdReturnsIdWithStringValue() {
        assertEquals("l1", Id.createLinkId("l1").toString());
    }

    @Test
    void networkCollectionsAreUnmodifiable() {
        Network network = new Network();
        assertThrows(UnsupportedOperationException.class, () -> network.getNodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> network.getLinks().clear());
    }

    @Test
    void nodeLinkCollectionsAreUnmodifiable() {
        Node node = new Node(Id.create("n1", Node.class), new Coord(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> node.getInLinks().clear());
        assertThrows(UnsupportedOperationException.class, () -> node.getOutLinks().clear());
    }

    @Test
    void networkDefaultConstructorCreatesEmptyNetwork() {
        Network network = new Network();
        assertEquals(0, network.getNodes().size());
        assertEquals(0, network.getLinks().size());
        assertNull(network.getName());
    }

    @Test
    void networkNamedConstructorStoresName() {
        Network network = new Network("my-network");
        assertEquals("my-network", network.getName());
    }

    @Test
    void networkAddNodeRejectsNull() {
        Network network = new Network();
        assertThrows(NullPointerException.class, () -> network.addNode(null));
    }

    @Test
    void networkAddLinkRejectsNull() {
        Network network = new Network();
        assertThrows(NullPointerException.class, () -> network.addLink(null));
    }
}
