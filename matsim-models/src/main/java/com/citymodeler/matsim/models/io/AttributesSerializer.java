package com.citymodeler.matsim.models.io;

import java.io.IOException;
import java.util.Map;

import javax.xml.namespace.QName;

import com.citymodeler.matsim.models.api.Attributes;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;

public final class AttributesSerializer extends JsonSerializer<Attributes> {
    private static final QName ATTRIBUTES = new QName("attributes");

    @Override
    public void serialize(Attributes attributes, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        if (!(generator instanceof ToXmlGenerator xmlGenerator)) {
            throw new MatsimModelException("Attributes can only be serialized with XmlMapper");
        }

        StringBuilder xml = new StringBuilder();
        for (Map.Entry<String, Object> entry : attributes.getAsMap().entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            xml.append("<attribute name=\"")
                    .append(escapeXml(entry.getKey()))
                    .append("\" class=\"")
                    .append(classHint(value))
                    .append("\">")
                    .append(escapeXml(value.toString()))
                    .append("</attribute>");
        }

        xmlGenerator.setNextName(ATTRIBUTES);
        xmlGenerator.writeStartObject();
        xmlGenerator.writeRaw(xml.toString());
        xmlGenerator.writeEndObject();
    }

    private static String classHint(Object value) {
        if (value instanceof String) {
            return String.class.getName();
        }
        if (value instanceof Double) {
            return Double.class.getName();
        }
        if (value instanceof Integer) {
            return Integer.class.getName();
        }
        if (value instanceof Long) {
            return Long.class.getName();
        }
        if (value instanceof Boolean) {
            return Boolean.class.getName();
        }
        return String.class.getName();
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
