package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

class AttributesSerdeTest {
    @Test
    void serializesAttributesWithMatsimClassHints() throws Exception {
        Attributes attributes = new Attributes();
        attributes.putAttribute("name", "Main Street");
        attributes.putAttribute("score", 12.5d);
        attributes.putAttribute("enabled", true);

        XmlMapper mapper = MatsimXmlMapperFactory.createXmlMapper();
        String xml = mapper.writeValueAsString(attributes);

        assertTrue(xml.contains("<attributes>"), xml);
        assertTrue(xml.contains("name=\"name\""), xml);
        assertTrue(xml.contains("class=\"java.lang.String\""), xml);
        assertTrue(xml.contains(">Main Street</attribute>"), xml);
        assertTrue(xml.contains("name=\"score\""), xml);
        assertTrue(xml.contains("class=\"java.lang.Double\""), xml);
        assertTrue(xml.contains(">12.5</attribute>"), xml);
        assertTrue(xml.contains("name=\"enabled\""), xml);
        assertTrue(xml.contains("class=\"java.lang.Boolean\""), xml);
        assertTrue(xml.contains(">true</attribute>"), xml);
    }

    @Test
    void serializesAttributesInStableKeyOrder() throws Exception {
        Attributes attributes = new Attributes();
        attributes.putAttribute("z", "last");
        attributes.putAttribute("b", "third");
        attributes.putAttribute("aa", "second");
        attributes.putAttribute("a", "first");

        XmlMapper mapper = MatsimXmlMapperFactory.createXmlMapper();
        String xml = mapper.writeValueAsString(attributes);

        assertTrue(xml.indexOf("name=\"a\"") < xml.indexOf("name=\"aa\""), xml);
        assertTrue(xml.indexOf("name=\"aa\"") < xml.indexOf("name=\"b\""), xml);
        assertTrue(xml.indexOf("name=\"b\"") < xml.indexOf("name=\"z\""), xml);
    }

    @Test
    void mapperRejectsDoctypeDeclarations() {
        String xml = """
                <!DOCTYPE attributes [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <attributes>
                    <attribute name=\"name\" class=\"java.lang.String\">&xxe;</attribute>
                </attributes>
                """;

        XmlMapper mapper = MatsimXmlMapperFactory.createXmlMapper();

        assertThrows(Exception.class, () -> mapper.readValue(xml, Attributes.class));
    }

    @Test
    void deserializesAttributesUsingMatsimClassHints() throws Exception {
        String xml = """
                <attributes>
                    <attribute name=\"name\" class=\"java.lang.String\">Main Street</attribute>
                    <attribute name=\"score\" class=\"java.lang.Double\">12.5</attribute>
                    <attribute name=\"enabled\" class=\"java.lang.Boolean\">true</attribute>
                </attributes>
                """;

        XmlMapper mapper = MatsimXmlMapperFactory.createXmlMapper();
        Attributes attributes = mapper.readValue(xml, Attributes.class);

        assertEquals("Main Street", attributes.getAttribute("name"));
        assertEquals(12.5d, attributes.getAttribute("score"));
        assertEquals(true, attributes.getAttribute("enabled"));
        assertInstanceOf(String.class, attributes.getAttribute("name"));
        assertInstanceOf(Double.class, attributes.getAttribute("score"));
        assertInstanceOf(Boolean.class, attributes.getAttribute("enabled"));
    }

    @Test
    void serializesAndDeserializesEmptyAttributes() throws Exception {
        XmlMapper mapper = MatsimXmlMapperFactory.createXmlMapper();

        String xml = mapper.writeValueAsString(new Attributes());
        Attributes attributes = mapper.readValue("<attributes/>", Attributes.class);

        assertTrue(xml.equals("<attributes/>") || xml.equals("<attributes></attributes>"));
        assertTrue(attributes.getAsMap().isEmpty());
    }

    @Test
    void serializesAndDeserializesDisallowedNextLinksAttribute() throws Exception {
        DisallowedNextLinks restriction = DisallowedNextLinks.empty()
                .plus("car", List.of("lA", "lB"))
                .plus("bus", List.of("lC"));

        Attributes attributes = new Attributes();
        attributes.putAttribute(XmlSupport.ATTR_DISALLOWED_NEXT_LINKS, restriction);

        XmlMapper mapper = MatsimXmlMapperFactory.createXmlMapper();
        String xml = mapper.writeValueAsString(attributes);

        assertTrue(xml.contains("class=\"" + XmlSupport.MATSIM_DISALLOWED_NEXT_LINKS_CLASS_HINT + "\""), xml);
        assertTrue(xml.contains("{&quot;bus&quot;:[[&quot;lC&quot;]],&quot;car&quot;:[[&quot;lA&quot;,&quot;lB&quot;]]}"), xml);

        Attributes deserialized = mapper.readValue(xml, Attributes.class);
        Object value = deserialized.getAttribute(XmlSupport.ATTR_DISALLOWED_NEXT_LINKS);
        assertInstanceOf(DisallowedNextLinks.class, value);
        assertEquals(restriction, value);
    }

    @Test
    void rejectsMalformedBooleanAttributeValues() {
        String xml = """
                <attributes>
                    <attribute name=\"enabled\" class=\"java.lang.Boolean\">yes</attribute>
                </attributes>
                """;

        XmlMapper mapper = MatsimXmlMapperFactory.createXmlMapper();
        Exception exception = assertThrows(Exception.class, () -> mapper.readValue(xml, Attributes.class));

        assertTrue(exception.getMessage().contains("yes"), exception.getMessage());
    }
}
