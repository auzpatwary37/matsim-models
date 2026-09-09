package com.citymodeler.matsim.models.io;

import java.io.IOException;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public final class AttributesDeserializer extends JsonDeserializer<Attributes> {
    @Override
    public Attributes deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode root = parser.getCodec().readTree(parser);
        Attributes attributes = new Attributes();
        JsonNode attributeNodes = root.get("attribute");
        if (attributeNodes == null || attributeNodes.isNull()) {
            return attributes;
        }

        if (attributeNodes.isArray()) {
            for (JsonNode attributeNode : attributeNodes) {
                addAttribute(attributes, attributeNode);
            }
        } else {
            addAttribute(attributes, attributeNodes);
        }
        return attributes;
    }

    private static void addAttribute(Attributes attributes, JsonNode attributeNode) {
        JsonNode nameNode = attributeNode.get("name");
        if (nameNode == null || nameNode.asText().isBlank()) {
            return;
        }

        String name = nameNode.asText();
        String className = text(attributeNode.get("class"));
        String value = attributeValue(attributeNode);
        attributes.putAttribute(name, convertValue(name, value, className));
    }

    private static String attributeValue(JsonNode attributeNode) {
        String value = text(attributeNode.get(""));
        if (value != null) {
            return value;
        }
        value = text(attributeNode.get("value"));
        if (value != null) {
            return value;
        }
        value = text(attributeNode.get("#text"));
        return value == null ? attributeNode.asText() : value;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static Object convertValue(String name, String value, String className) {
        if (XmlSupport.ATTR_DISALLOWED_NEXT_LINKS.equals(name)
                || XmlSupport.MATSIM_DISALLOWED_NEXT_LINKS_CLASS_HINT.equals(className)
                || XmlSupport.LEGACY_MATSIM_DISALLOWED_NEXT_LINKS_CLASS_HINT.equals(className)) {
            try {
                return DisallowedNextLinks.fromJson(value);
            } catch (MatsimModelException exception) {
                return value;
            }
        }
        if (Double.class.getName().equals(className)) {
            return Double.valueOf(value);
        }
        if (Integer.class.getName().equals(className)) {
            return Integer.valueOf(value);
        }
        if (Long.class.getName().equals(className)) {
            return Long.valueOf(value);
        }
        if (Boolean.class.getName().equals(className)) {
            if ("true".equalsIgnoreCase(value)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(value)) {
                return Boolean.FALSE;
            }
            throw new MatsimModelException("Invalid boolean attribute value: " + value);
        }
        return value;
    }
}
