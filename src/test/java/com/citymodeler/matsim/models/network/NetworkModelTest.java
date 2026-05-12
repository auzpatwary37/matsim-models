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
        Link defaultConstructorLink = new Link(Id.createLinkId("l0"), Id.create("n1", Node.class), Id.create("n2", Node.class), 100, 1000, 13.89, 1);
        Link nullModesLink = new Link(Id.createLinkId("l1"), Id.create("n1", Node.class), Id.create("n2", Node.class), 100, 1000, 13.89, 1, null);
        Link emptyModesLink = new Link(Id.createLinkId("l2"), Id.create("n1", Node.class), Id.create("n2", Node.class), 100, 1000, 13.89, 1, Set.of());

        assertEquals(Set.of("car"), defaultConstructorLink.getAllowedModes());
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
    void networkCreateNodeCreatesAndAddsNode() {
        Network network = new Network();
        Node node = network.createNode("n1", 10.0, 20.0);

        assertEquals("n1", node.getId().toString());
        assertEquals(10.0, node.getCoord().getX());
        assertEquals(20.0, node.getCoord().getY());
        assertSame(node, network.getNodes().get(node.getId()));
    }

    @Test
    void networkCreateLinkCreatesAndAddsLink() {
        Network network = new Network();
        network.createNode("n1", 0, 0);
        network.createNode("n2", 100, 0);
        Link link = network.createLink("l1", "n1", "n2", 100, 1000, 13.89, 1, Set.of("car"));

        assertEquals("l1", link.getId().toString());
        assertSame(link, network.getLinks().get(link.getId()));
    }

    @Test
    void networkRemoveLinkRemovesFromNetworkAndNodeRefs() {
        Network network = new Network();
        Node n1 = network.createNode("n1", 0, 0);
        Node n2 = network.createNode("n2", 100, 0);
        Link link = network.createLink("l1", "n1", "n2", 100, 1000, 13.89, 1, Set.of("car"));
        network.postProcess();

        assertEquals(1, n1.getOutLinks().size());
        network.removeLink(link.getId());

        assertNull(network.getLinks().get(link.getId()));
        assertEquals(0, n1.getOutLinks().size());
        assertEquals(0, n2.getInLinks().size());
    }

    @Test
    void networkRemoveNodeRemovesNodeAndIncidentLinks() {
        Network network = new Network();
        network.createNode("n1", 0, 0);
        network.createNode("n2", 100, 0);
        network.createLink("l1", "n1", "n2", 100, 1000, 13.89, 1, Set.of("car"));
        network.postProcess();

        assertEquals(1, network.getLinks().size());
        network.removeNode(Id.create("n1", Node.class));

        assertNull(network.getNodes().get(Id.create("n1", Node.class)));
        assertEquals(0, network.getLinks().size());
    }

    @Test
    void linkSettersUpdateAttributes() {
        Link link = new Link(Id.createLinkId("l1"), Id.create("n1", Node.class), Id.create("n2", Node.class), 100, 1000, 13.89, 1, Set.of("car"));

        link.setLength(200);
        link.setCapacity(2000);
        link.setFreespeed(20.0);
        link.setNumberOfLanes(2);
        link.setAllowedModes(Set.of("car", "bus"));

        assertEquals(200, link.getLength());
        assertEquals(2000, link.getCapacity());
        assertEquals(20.0, link.getFreespeed());
        assertEquals(2, link.getNumberOfLanes());
        assertEquals(Set.of("car", "bus"), link.getAllowedModes());
    }

    @Test
    void createLinkWiresTopologyImmediatelyWithoutPostProcess() {
        Network network = new Network("test");
        Node n1 = network.createNode("n1", 0, 0);
        Node n2 = network.createNode("n2", 100, 0);
        Link link = network.createLink("l1", "n1", "n2", 100, 1000, 13.89, 1, Set.of("car"));

        assertSame(n1, link.getFromNode());
        assertSame(n2, link.getToNode());
        assertSame(link, n1.getOutLinks().get(link.getId()));
        assertSame(link, n2.getInLinks().get(link.getId()));
    }

    @Test
    void removeNodeRemovesIncidentLinksWithoutPostProcess() {
        Network network = new Network();
        network.createNode("n1", 0, 0);
        network.createNode("n2", 100, 0);
        network.createLink("l1", "n1", "n2", 100, 1000, 13.89, 1, Set.of("car"));

        network.removeNode(Id.create("n1", Node.class));
        assertNull(network.getNodes().get(Id.create("n1", Node.class)));
        assertEquals(0, network.getLinks().size());
        assertNull(network.getLinks().get(Id.create("l1", Link.class)));
    }

    @Test
    void createNodeRejectsDuplicateId() {
        Network network = new Network();
        network.createNode("n1", 0, 0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> network.createNode("n1", 10, 20));
        assertTrue(ex.getMessage().contains("n1"));
    }

    @Test
    void addNodeRejectsDuplicateId() {
        Network network = new Network();
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(0, 0));
        network.addNode(n1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> network.addNode(new Node(Id.create("n1", Node.class), new Coord(10, 20))));
        assertTrue(ex.getMessage().contains("n1"));
    }

    @Test
    void addLinkRejectsDuplicateId() {
        Network network = new Network();
        network.createNode("n1", 0, 0);
        network.createNode("n2", 100, 0);
        network.createLink("l1", "n1", "n2", 100, 1000, 13.89, 1, Set.of("car"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> network.createLink("l1", "n1", "n2", 200, 2000, 13.89, 2, Set.of("bus")));
        assertTrue(ex.getMessage().contains("l1"));
    }
}
