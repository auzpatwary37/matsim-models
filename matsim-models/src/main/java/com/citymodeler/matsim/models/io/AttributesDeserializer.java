package com.citymodeler.matsim.models.io;

import java.io.IOException;

import com.citymodeler.matsim.models.api.Attributes;
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

        String className = text(attributeNode.get("class"));
        String value = attributeValue(attributeNode);
        attributes.putAttribute(nameNode.asText(), convertValue(value, className));
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

    private static Object convertValue(String value, String className) {
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
            return Boolean.valueOf(value);
        }
        return value;
    }
}
