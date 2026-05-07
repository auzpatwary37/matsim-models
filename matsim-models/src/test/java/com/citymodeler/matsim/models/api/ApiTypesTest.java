package com.citymodeler.matsim.models.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ApiTypesTest {
    @Test
    void coordExposesValuesAndUsesValueEquality() {
        Coord coord = new Coord(12.5, -4.25);

        assertEquals(12.5, coord.getX());
        assertEquals(-4.25, coord.getY());
        assertEquals(new Coord(12.5, -4.25), coord);
        assertEquals(new Coord(12.5, -4.25).hashCode(), coord.hashCode());
        assertNotEquals(new Coord(12.5, 4.25), coord);
        assertTrue(coord.toString().contains("12.5"));
        assertTrue(coord.toString().contains("-4.25"));
    }

    @Test
    void idEqualityUsesOnlyTheStringValueAcrossGenericTypes() {
        Id<TestNode> nodeId = Id.create("same-id", TestNode.class);
        Id<TestLink> linkId = Id.create("same-id", TestLink.class);

        assertEquals(nodeId, linkId);
        assertEquals(nodeId.hashCode(), linkId.hashCode());
    }

    @Test
    void idToStringReturnsWrappedValue() {
        assertEquals("abc123", Id.create("abc123", TestNode.class).toString());
    }

    @Test
    void idRejectsNullValues() {
        assertThrows(NullPointerException.class, () -> Id.create(null, TestNode.class));
    }

    @Test
    void tupleExposesFirstAndSecondValues() {
        Tuple<String, Integer> tuple = new Tuple<>("left", 42);

        assertEquals("left", tuple.getFirst());
        assertEquals(42, tuple.getSecond());
    }

    @Test
    void attributesCanPutGetRemoveAndTreatNullValuesAsRemoval() {
        Attributes attributes = new Attributes();

        attributes.putAttribute("speed", 50);
        assertEquals(50, attributes.getAttribute("speed"));

        attributes.putAttribute("speed", null);
        assertEquals(null, attributes.getAttribute("speed"));

        attributes.putAttribute("name", "main");
        assertEquals("main", attributes.removeAttribute("name"));
        assertEquals(null, attributes.getAttribute("name"));
    }

    @Test
    void attributesMapCannotBeMutatedDirectly() {
        Attributes attributes = new Attributes();
        attributes.putAttribute("lanes", 2);

        Map<String, Object> map = attributes.getAsMap();

        assertEquals(2, map.get("lanes"));
        assertThrows(UnsupportedOperationException.class, () -> map.put("lanes", 3));
        assertEquals(2, attributes.getAttribute("lanes"));
    }

    private static final class TestNode {
    }

    private static final class TestLink {
    }
}
