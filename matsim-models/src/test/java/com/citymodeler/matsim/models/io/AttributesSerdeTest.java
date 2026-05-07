package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Attributes;
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
}
