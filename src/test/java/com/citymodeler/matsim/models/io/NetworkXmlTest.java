package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

class NetworkXmlTest {
    @TempDir
    Path tempDir;

    @Test
    void readsWritesAndReadsNetworkXml() {
        String xml = """
                <network name=\"test-network\">
                    <attributes>
                        <attribute name=\"network-source\" class=\"java.lang.String\">unit-test</attribute>
                    </attributes>
                    <nodes>
                        <node id=\"n1\" x=\"1.0\" y=\"2.0\">
                            <attributes>
                                <attribute name=\"node-kind\" class=\"java.lang.String\">origin</attribute>
                            </attributes>
                        </node>
                        <node id=\"n2\" x=\"3.0\" y=\"4.0\" />
                    </nodes>
                    <links>
                        <link id=\"l1\" from=\"n1\" to=\"n2\" length=\"100.0\" capacity=\"900.0\" freespeed=\"13.9\" permlanes=\"2.0\" modes=\"car,bus\">
                            <attributes>
                                <attribute name=\"link-kind\" class=\"java.lang.String\">arterial</attribute>
                            </attributes>
                        </link>
                    </links>
                </network>
                """;

        Network network = new NetworkXmlReader().read(xml);
        String roundTrippedXml = new NetworkXmlWriter().writeToString(network);
        Network roundTripped = new NetworkXmlReader().read(roundTrippedXml);

        assertEquals(2, roundTripped.getNodes().size());
        assertEquals(1, roundTripped.getLinks().size());
        assertEquals("unit-test", roundTripped.getAttributes().getAttribute("network-source"));

        Node node = roundTripped.getNodes().get(Id.create("n1", Node.class));
        assertEquals(1.0, node.getCoord().getX());
        assertEquals("origin", node.getAttributes().getAttribute("node-kind"));

        Link link = roundTripped.getLinks().get(Id.create("l1", Link.class));
        assertEquals(100.0, link.getLength());
        assertEquals(900.0, link.getCapacity());
        assertEquals(13.9, link.getFreespeed());
        assertEquals(2.0, link.getNumberOfLanes());
        assertEquals("arterial", link.getAttributes().getAttribute("link-kind"));
        assertEquals(2, link.getAllowedModes().size());
        assertEquals(node, link.getFromNode());
        assertNotNull(link.getToNode());
        assertEquals(link, node.getOutLinks().get(link.getId()));
    }

    @Test
    void writesAttributesInStableKeyOrder() {
        Network network = new Network("ordered-attributes");
        network.getAttributes().putAttribute("b", "second");
        network.getAttributes().putAttribute("a", "first");

        String xml = new NetworkXmlWriter().writeToString(network);

        assertTrue(xml.indexOf("name=\"a\"") < xml.indexOf("name=\"b\""), xml);
    }

    @Test
    void allowsDoctypeDeclarationsWithoutResolvingExternalEntities() {
        String xml = """
                <!DOCTYPE network [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <network>
                    <nodes>
                        <node id="n1" x="0.0" y="0.0" />
                    </nodes>
                    <links />
                </network>
                """;

        assertDoesNotThrow(() -> new NetworkXmlReader().read(xml));
    }

    @Test
    void loadFromClasspathFixture() {
        String fixturePath = "fixtures/network.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        Network network = new NetworkXmlReader().read(is);

        assertNotNull(network);
        assertEquals(4, network.getNodes().size());
        assertEquals(3, network.getLinks().size());
        assertEquals("city-modeler-test", network.getAttributes().getAttribute("network-source"));

        Node n1 = network.getNodes().get(Id.create("n1", Node.class));
        assertEquals(0.0, n1.getCoord().getX());
        assertEquals("origin", n1.getAttributes().getAttribute("node-kind"));
    }

    @Test
    void writesAndReadsGzipPath() {
        Network network = new NetworkXmlReader().read(Path.of("src/test/resources/fixtures/network.xml"));
        Path gzipPath = tempDir.resolve("network.xml.gz");

        new NetworkXmlWriter().write(network, gzipPath);
        Network roundTripped = new NetworkXmlReader().read(gzipPath);

        assertEquals(network.getNodes().size(), roundTripped.getNodes().size());
        assertEquals(network.getLinks().size(), roundTripped.getLinks().size());
    }
}
