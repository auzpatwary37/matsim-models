package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

class MappingGeoJsonWriterTest {

    private Network network;

    @BeforeEach
    void setUp() {
        network = new Network();
        Node a = new Node(Id.create("A", Node.class), new Coord(0, 0));
        Node b = new Node(Id.create("B", Node.class), new Coord(100, 0));
        network.addNode(a);
        network.addNode(b);
        network.addLink(new Link(Id.create("l1", Link.class), a.getId(), b.getId(),
                100.0, 900.0, 13.9, 2.0, Set.of("car", "pt")));
        network.postProcess();
    }

    private TransitSchedule scheduleWithStops() {
        TransitSchedule schedule = new TransitSchedule();
        // Intentionally added out of id order to prove the writer sorts deterministically.
        TransitStopFacility bStop = new TransitStopFacility(
                Id.create("stop_b", TransitStopFacility.class), new Coord(50, 5), false);
        bStop.setName("Beta");
        bStop.setLinkId(Id.create("l1", Link.class));
        TransitStopFacility aStop = new TransitStopFacility(
                Id.create("stop_a", TransitStopFacility.class), new Coord(25, 5), false);
        aStop.setName("Alpha \"quoted\"");
        aStop.setLinkId(Id.create("l1", Link.class));
        schedule.addStopFacility(bStop);
        schedule.addStopFacility(aStop);
        return schedule;
    }

    @Test
    void writesValidFeatureCollectionWithOneFeaturePerFacility() throws Exception {
        TransitSchedule schedule = scheduleWithStops();

        String json = new MappingGeoJsonWriter().writeToString(schedule, network);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        assertEquals("FeatureCollection", root.get("type").asText());
        JsonNode features = root.get("features");
        assertTrue(features.isArray());
        assertEquals(schedule.getFacilities().size(), features.size());

        for (JsonNode feature : features) {
            assertEquals("Feature", feature.get("type").asText());
            assertEquals("Point", feature.get("geometry").get("type").asText());
            assertEquals(2, feature.get("geometry").get("coordinates").size());
            assertTrue(feature.get("properties").has("facilityId"));
            assertTrue(feature.get("properties").has("linkId"),
                    "every feature carries a linkId property");
            assertTrue(feature.get("properties").has("name"));
        }
    }

    @Test
    void featuresAreSortedByFacilityIdAndCarryCorrectValues() throws Exception {
        TransitSchedule schedule = scheduleWithStops();

        String json = new MappingGeoJsonWriter().writeToString(schedule, network);
        JsonNode features = new ObjectMapper().readTree(json).get("features");

        assertEquals("stop_a", features.get(0).get("properties").get("facilityId").asText());
        assertEquals("stop_b", features.get(1).get("properties").get("facilityId").asText());
        assertEquals("l1", features.get(0).get("properties").get("linkId").asText());
        assertEquals(25.0, features.get(0).get("geometry").get("coordinates").get(0).asDouble());
        assertEquals(5.0, features.get(0).get("geometry").get("coordinates").get(1).asDouble());
        assertEquals("Alpha \"quoted\"", features.get(0).get("properties").get("name").asText());
    }

    @Test
    void outputIsDeterministicAcrossCalls() {
        TransitSchedule schedule = scheduleWithStops();
        MappingGeoJsonWriter writer = new MappingGeoJsonWriter();

        String first = writer.writeToString(schedule, network);
        String second = writer.writeToString(schedule, network);

        assertEquals(first, second, "GeoJSON output must be byte-for-byte deterministic");
    }

    @Test
    void writeToPathAndStreamMatchWriteToString() throws Exception {
        TransitSchedule schedule = scheduleWithStops();
        MappingGeoJsonWriter writer = new MappingGeoJsonWriter();
        String expected = writer.writeToString(schedule, network);

        Path tmp = Files.createTempFile("mapping", ".geojson");
        try {
            writer.write(schedule, network, tmp);
            assertEquals(expected, Files.readString(tmp, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(tmp);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(schedule, network, out);
        assertEquals(expected, out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void nullLinkIdIsWrittenAsJsonNull() throws Exception {
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop = new TransitStopFacility(
                Id.create("s", TransitStopFacility.class), new Coord(1, 2), false);
        stop.setName(null);
        schedule.addStopFacility(stop);

        String json = new MappingGeoJsonWriter().writeToString(schedule, network);
        JsonNode props = new ObjectMapper().readTree(json).get("features").get(0).get("properties");
        assertTrue(props.get("linkId").isNull());
        assertTrue(props.get("name").isNull());
        assertTrue(props.get("candidateLinkIds").isArray());
        assertEquals(0, props.get("candidateLinkIds").size(),
                "a facility referenced by no route has no candidate mode -> no candidates");
    }

    @Test
    void candidateLinkIdsAreEmittedForAStopWithSeveralNearbyLinks() throws Exception {
        // Two parallel, mode-compatible links next to the stop; a bus route references the stop so
        // the writer knows which mode to score candidates with.
        Network rich = new Network();
        Node n0 = new Node(Id.create("n0", Node.class), new Coord(0, 0));
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(200, 0));
        rich.addNode(n0);
        rich.addNode(n1);
        rich.addLink(new Link(Id.create("a", Link.class), n0.getId(), n1.getId(),
                200.0, 900.0, 13.9, 2.0, Set.of("car", "bus")));
        rich.addLink(new Link(Id.create("b", Link.class), n0.getId(), n1.getId(),
                200.0, 900.0, 13.9, 2.0, Set.of("car", "bus")));
        rich.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop = new TransitStopFacility(
                Id.create("stop", TransitStopFacility.class), new Coord(100, 5), false);
        stop.setName("Main Street");
        stop.setLinkId(Id.create("a", Link.class));
        schedule.addStopFacility(stop);

        TransitLine line = new TransitLine(Id.create("L1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("R1", TransitRoute.class));
        route.setTransportMode("bus");
        route.addStop(new TransitRouteStop(stop.getId(), 0, 0, false));
        line.addRoute(route);
        schedule.addTransitLine(line);

        String json = new MappingGeoJsonWriter().writeToString(schedule, rich);
        JsonNode props = new ObjectMapper().readTree(json)
                .get("features").get(0).get("properties");

        JsonNode candidates = props.get("candidateLinkIds");
        assertTrue(candidates.isArray());
        assertTrue(candidates.size() > 1,
                "candidateLinkIds must include every nearby candidate link: " + candidates);
        assertEquals("a", candidates.get(0).asText());
        assertEquals("b", candidates.get(1).asText());
    }
}
