package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks;

class DisallowedNextLinksXmlRoundTripTest {

    private static final DisallowedNextLinks RESTRICTION =
            DisallowedNextLinks.empty().plus("car", List.of("lA", "lB"));

    private static Network networkWithRestriction() {
        Network network = new Network("restriction-net");
        network.addNode(new Node(Id.create("n1", Node.class), new Coord(0.0, 0.0)));
        network.addNode(new Node(Id.create("n2", Node.class), new Coord(10.0, 0.0)));
        Link link = new Link(
                Id.create("l1", Link.class),
                Id.create("n1", Node.class),
                Id.create("n2", Node.class),
                10.0, 600.0, 13.9, 1.0, Set.of("car"));
        link.getAttributes().putAttribute(XmlSupport.ATTR_DISALLOWED_NEXT_LINKS, RESTRICTION);
        network.addLink(link);
        return network;
    }

    @Test
    void writeAndReadRoundTripsRestriction() {
        String xml = new NetworkXmlWriter().writeToString(networkWithRestriction());
        assertTrue(xml.contains("class=\"" + XmlSupport.MATSIM_DISALLOWED_NEXT_LINKS_CLASS_HINT + "\""));
        assertTrue(xml.contains("{\"car\":[[\"lA\",\"lB\"]]}"));

        Network read = new NetworkXmlReader().read(xml);
        Link link = read.getLinks().get(Id.create("l1", Link.class));
        Object attribute = link.getAttributes().getAttribute(XmlSupport.ATTR_DISALLOWED_NEXT_LINKS);
        assertInstanceOf(DisallowedNextLinks.class, attribute);
        assertEquals(RESTRICTION, attribute);
    }

    @Test
    void readsHistoricAliasClassHint() {
        String xml = """
                <network>
                    <nodes>
                        <node id="n1" x="0.0" y="0.0" />
                        <node id="n2" x="10.0" y="0.0" />
                    </nodes>
                    <links>
                        <link id="l1" from="n1" to="n2" length="10.0" capacity="600.0" freespeed="13.9" permlanes="1.0" modes="car">
                            <attributes>
                                <attribute name="disallowedNextLinks" class="org.matsim.core.network.DisallowedNextLinks">{&quot;car&quot;:[[&quot;lA&quot;,&quot;lB&quot;]]}</attribute>
                            </attributes>
                        </link>
                    </links>
                </network>
                """;

        Network read = new NetworkXmlReader().read(xml);
        Link link = read.getLinks().get(Id.create("l1", Link.class));
        Object attribute = link.getAttributes().getAttribute(XmlSupport.ATTR_DISALLOWED_NEXT_LINKS);
        assertInstanceOf(DisallowedNextLinks.class, attribute);
        assertEquals(RESTRICTION, attribute);
    }

    @Test
    void readsByAttributeNameWhenClassHintIsPlainString() {
        String xml = """
                <network>
                    <nodes>
                        <node id="n1" x="0.0" y="0.0" />
                        <node id="n2" x="10.0" y="0.0" />
                    </nodes>
                    <links>
                        <link id="l1" from="n1" to="n2" length="10.0" capacity="600.0" freespeed="13.9" permlanes="1.0" modes="car">
                            <attributes>
                                <attribute name="disallowedNextLinks" class="java.lang.String">{&quot;car&quot;:[[&quot;lA&quot;,&quot;lB&quot;]]}</attribute>
                            </attributes>
                        </link>
                    </links>
                </network>
                """;

        Network read = new NetworkXmlReader().read(xml);
        Link link = read.getLinks().get(Id.create("l1", Link.class));
        Object attribute = link.getAttributes().getAttribute(XmlSupport.ATTR_DISALLOWED_NEXT_LINKS);
        assertInstanceOf(DisallowedNextLinks.class, attribute);
        assertEquals(RESTRICTION, attribute);
    }

    @Test
    void malformedPayloadFallsBackToRawStringWithoutCorruptingOtherAttributes() {
        String xml = """
                <network>
                    <nodes>
                        <node id="n1" x="0.0" y="0.0" />
                        <node id="n2" x="10.0" y="0.0" />
                    </nodes>
                    <links>
                        <link id="l1" from="n1" to="n2" length="10.0" capacity="600.0" freespeed="13.9" permlanes="1.0" modes="car">
                            <attributes>
                                <attribute name="disallowedNextLinks" class="org.matsim.core.network.turnRestrictions.DisallowedNextLinks">not-json</attribute>
                                <attribute name="note" class="java.lang.String">kept</attribute>
                            </attributes>
                        </link>
                    </links>
                </network>
                """;

        Network read = new NetworkXmlReader().read(xml);
        Link link = read.getLinks().get(Id.create("l1", Link.class));
        assertEquals("not-json", link.getAttributes().getAttribute(XmlSupport.ATTR_DISALLOWED_NEXT_LINKS));
        assertEquals("kept", link.getAttributes().getAttribute("note"));
    }
}
