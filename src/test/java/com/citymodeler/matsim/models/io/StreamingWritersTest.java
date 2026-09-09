package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

class StreamingWritersTest {
    @TempDir
    Path tempDir;

    private static Network buildMediumNetwork(int nodeCount, int linkCount) {
        Network network = new Network("medium");
        network.getAttributes().putAttribute("source", "streaming-test");
        for (int i = 1; i <= nodeCount; i++) {
            network.addNode(new Node(Id.create("n" + i, Node.class), new Coord(i * 10.0, 0.0)));
        }
        for (int i = 1; i <= linkCount; i++) {
            int from = (i - 1) % nodeCount + 1;
            int to = i % nodeCount + 1;
            Link link = new Link(
                    Id.createLinkId("l" + i),
                    Id.create("n" + from, Node.class),
                    Id.create("n" + to, Node.class),
                    100.0 + i, 1000.0, 13.9, 1.0, Set.of("car"));
            link.getAttributes().putAttribute("index", (double) i);
            network.addLink(link);
        }
        return network;
    }

    @Test
    void mediumNetworkProducesEquivalentModels() throws Exception {
        Network network = buildMediumNetwork(500, 1000);

        Path domPath = tempDir.resolve("dom-network.xml");
        new NetworkXmlWriter().write(network, domPath);
        Network domRead = new NetworkXmlReader().read(domPath);

        Path streamPath = tempDir.resolve("stream-network.xml");
        new StreamingNetworkWriter().write(network, streamPath);
        Network streamRead = new NetworkXmlReader().read(streamPath);

        assertEquals(domRead.getNodes().keySet(), streamRead.getNodes().keySet());
        assertEquals(domRead.getLinks().keySet(), streamRead.getLinks().keySet());
        assertEquals(domRead.getAttributes().getAttribute("source"), streamRead.getAttributes().getAttribute("source"));
        for (Link domLink : domRead.getLinks().values()) {
            Link streamLink = streamRead.getLinks().get(domLink.getId());
            assertEquals(domLink.getFromNodeId(), streamLink.getFromNodeId());
            assertEquals(domLink.getToNodeId(), streamLink.getToNodeId());
            assertEquals(domLink.getLength(), streamLink.getLength());
            assertEquals(domLink.getAllowedModes(), streamLink.getAllowedModes());
            assertEquals(domLink.getAttributes().getAttribute("index"), streamLink.getAttributes().getAttribute("index"));
        }
    }

    @Test
    void smallNetworkMatchesDomWriterModuloWhitespace() {
        Network network = new Network("tiny");
        Id<Node> fromId = Id.create("n1", Node.class);
        Id<Node> toId = Id.create("n2", Node.class);
        network.addNode(new Node(fromId, new Coord(0.0, 0.0)));
        network.addNode(new Node(toId, new Coord(10.0, 0.0)));
        Link emptyModeLink = new Link(Id.createLinkId("l1"), fromId, toId, 10.0, 100.0, 10.0, 1.0, new java.util.LinkedHashSet<>());
        Link restrictedLink = new Link(Id.createLinkId("l2"), fromId, toId, 10.0, 100.0, 10.0, 1.0, Set.of("car"));
        restrictedLink.getAttributes().putAttribute(XmlSupport.ATTR_DISALLOWED_NEXT_LINKS,
                DisallowedNextLinks.empty().plus("car", List.of("lX", "lY")));
        network.addLink(emptyModeLink);
        network.addLink(restrictedLink);

        String domXml = new NetworkXmlWriter().writeToString(network);
        String streamXml = new StreamingNetworkWriter().writeToString(network);

        assertEquals(canonicalize(domXml), canonicalize(streamXml));
    }

    @Test
    void capacityPeriodOptionEmittedOnLinksElementOnlyWhenRequested() {
        Network network = new Network("capped");
        Id<Node> fromId = Id.create("n1", Node.class);
        Id<Node> toId = Id.create("n2", Node.class);
        network.addNode(new Node(fromId, new Coord(0.0, 0.0)));
        network.addNode(new Node(toId, new Coord(10.0, 0.0)));
        network.addLink(new Link(Id.createLinkId("l1"), fromId, toId, 10.0, 100.0, 10.0, 1.0, Set.of("car")));

        String without = new StreamingNetworkWriter().writeToString(network);
        String with = new StreamingNetworkWriter(3600.0).writeToString(network);

        assertFalse(without.contains("capperiod"), without);
        assertTrue(with.contains("capperiod=\"3600.0\""), with);
    }

    @Test
    void scheduleWithRouteSequenceRoundTripsThroughStreamingWriter() throws Exception {
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility facility = new TransitStopFacility(
                Id.create("stop-1", TransitStopFacility.class), new Coord(1.0, 2.0), false);
        facility.setLinkId(Id.create("l1", Link.class));
        schedule.addStopFacility(facility);
        TransitLine line = new TransitLine(Id.create("line-1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route-1", TransitRoute.class));
        route.setTransportMode("bus");
        route.addStop(new TransitRouteStop(Id.create("stop-1", TransitStopFacility.class), 0.0, 10.0, true));
        route.setNetworkRoute(List.of(Id.create("l1", Link.class), Id.create("l2", Link.class)));
        route.addDeparture(new Departure(Id.create("dep-1", Departure.class), 3600.0));
        line.addRoute(route);
        schedule.addTransitLine(line);

        Path path = tempDir.resolve("stream-schedule.xml");
        new StreamingTransitScheduleWriter().write(schedule, path);
        TransitSchedule roundTripped = new TransitScheduleXmlReader().read(path);

        TransitRoute readRoute = roundTripped.getTransitLines().get(Id.create("line-1", TransitLine.class))
                .getRoutes().get(Id.create("route-1", TransitRoute.class));
        assertEquals("bus", readRoute.getTransportMode());
        assertEquals(List.of(Id.create("l1", Link.class), Id.create("l2", Link.class)), readRoute.getNetworkRoute());
        assertEquals(10.0, readRoute.getStops().get(0).getDepartureOffset());
        assertEquals(Id.create("l1", Link.class),
                roundTripped.getFacilities().get(Id.create("stop-1", TransitStopFacility.class)).getLinkId());
        assertEquals(3600.0,
                readRoute.getDepartures().get(Id.create("dep-1", Departure.class)).getDepartureTime());
    }

    @Test
    void gzipSuffixCompressesLikeExistingIo() throws Exception {
        Network network = buildMediumNetwork(50, 100);
        Path gzPath = tempDir.resolve("network.xml.gz");
        new StreamingNetworkWriter().write(network, gzPath);
        Network read = new NetworkXmlReader().read(gzPath);
        assertEquals(50, read.getNodes().size());
        assertEquals(100, read.getLinks().size());
    }

    /**
     * Parses the XML and re-serializes it with attributes sorted per element,
     * so comparisons ignore whitespace, declarations and attribute order
     * (all semantically irrelevant).
     */
    private static String canonicalize(String xml) {
        org.w3c.dom.Document document = XmlSupport.parse(xml);
        removeWhitespaceTextNodes(document.getDocumentElement());
        var elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) elements.item(i);
            var attributes = element.getAttributes();
            List<String> names = new java.util.ArrayList<>();
            for (int a = 0; a < attributes.getLength(); a++) {
                names.add(((org.w3c.dom.Attr) attributes.item(a)).getName());
            }
            names.sort(java.util.Comparator.reverseOrder());
            List<String> values = names.stream().map(n -> element.getAttribute(n)).toList();
            names.forEach(element::removeAttribute);
            for (int a = 0; a < names.size(); a++) {
                element.setAttribute(names.get(a), values.get(a));
            }
        }
        return XmlSupport.writeToString(document);
    }

    private static void removeWhitespaceTextNodes(org.w3c.dom.Node node) {
        var children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE
                    && child.getTextContent().isBlank()) {
                node.removeChild(child);
            } else if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                removeWhitespaceTextNodes(child);
            }
        }
    }
}
