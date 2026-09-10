package com.citymodeler.matsim.models.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.NetworkXmlReader;
import com.citymodeler.matsim.models.io.NetworkXmlWriter;

class LinkEmptyModesRoundTripTest {

    private static Network networkWithEmptyModeLink() {
        Network network = new Network();
        Id<Node> fromId = Id.create("n1", Node.class);
        Id<Node> toId = Id.create("n2", Node.class);
        network.addNode(new Node(fromId, new Coord(0.0, 0.0)));
        network.addNode(new Node(toId, new Coord(10.0, 0.0)));
        network.addLink(new Link(
                Id.createLinkId("l1"), fromId, toId, 10.0, 100.0, 10.0, 1.0, new LinkedHashSet<>()));
        return network;
    }

    @Test
    void linkKeepsEmptyAllowedModes() {
        Id<Node> fromId = Id.create("n1", Node.class);
        Id<Node> toId = Id.create("n2", Node.class);
        Link link = new Link(Id.createLinkId("l1"), fromId, toId, 10.0, 100.0, 10.0, 1.0, new LinkedHashSet<>());
        assertTrue(link.getAllowedModes().isEmpty());

        link.setAllowedModes(new LinkedHashSet<>());
        assertTrue(link.getAllowedModes().isEmpty());

        link.setAllowedModes(null);
        assertTrue(link.getAllowedModes().isEmpty());
    }

    @Test
    void emptyModesSurviveXmlRoundTrip() {
        Network network = networkWithEmptyModeLink();
        String xml = new NetworkXmlWriter().writeToString(network);
        assertTrue(xml.contains("modes=\"\""), xml);

        Network roundTripped = new NetworkXmlReader().read(xml);
        Link link = roundTripped.getLinks().get(Id.createLinkId("l1"));
        assertTrue(link.getAllowedModes().isEmpty(),
                "expected empty modes but was " + link.getAllowedModes());
    }

    @Test
    void setModesStillWorksForNonEmpty() {
        Id<Node> fromId = Id.create("n1", Node.class);
        Id<Node> toId = Id.create("n2", Node.class);
        Link link = new Link(Id.createLinkId("l1"), fromId, toId, 10.0, 100.0, 10.0, 1.0, Set.of("car"));
        link.setAllowedModes(Set.of("bus", "pt"));
        assertTrue(link.getAllowedModes().containsAll(Set.of("bus", "pt")));
        assertTrue(link.getAllowedModes().size() == 2);
    }
}
