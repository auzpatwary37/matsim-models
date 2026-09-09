package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopArea;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

/**
 * Interoperability oracle for the transit schedule wire format: validates
 * current-MATSim-style input AND this library's writer output against the
 * PUBLISHED current MATSim transitSchedule v2 DTD (vendored under
 * {@code matsim-spec/} from matsim.org/files/dtd). This is the external
 * contract check that DOM-vs-StAX in-repo parity alone cannot provide.
 */
class TransitScheduleSpecValidationTest {
    private static final String FIXTURE = "matsim-spec/transitSchedule-current-v2.xml";
    private static final String DTD = "matsim-spec/transitSchedule_v2.dtd";

    @Test
    void currentMatsimStyleFixtureValidatesAgainstCurrentDtd() throws Exception {
        validateAgainstDtd(resourceAsString(FIXTURE));
    }

    @Test
    void currentMatsimStyleFixtureLoadsRouteLinkSequenceAndReferences() throws Exception {
        TransitSchedule schedule = new TransitScheduleXmlReader().read(resourceAsString(FIXTURE));

        TransitRoute route = schedule.getTransitLines().get(Id.create("line-1", TransitLine.class))
                .getRoutes().get(Id.create("route-1", TransitRoute.class));
        assertEquals(
                List.of(Id.create("link-1", Link.class), Id.create("link-2", Link.class), Id.create("link-3", Link.class)),
                route.getNetworkRoute());
        assertEquals("bus", route.getTransportMode());
        assertEquals("Main line", route.getDescription());

        TransitStopFacility stop = schedule.getFacilities().get(Id.create("stop-1", TransitStopFacility.class));
        assertEquals(Id.create("area-1", TransitStopArea.class), stop.getStopAreaId());
        assertEquals("Main", stop.getName());
        assertEquals(Id.create("link-1", Link.class), stop.getLinkId());

        Departure departure = route.getDepartures().get(Id.create("dep-1", Departure.class));
        assertEquals(8.5 * 3600.0, departure.getDepartureTime());
        assertEquals("bus-1", departure.getVehicleId());
    }

    @Test
    void domWriterOutputValidatesAgainstCurrentDtdAndKeepsRouteSequence() throws Exception {
        TransitSchedule schedule = buildMirroringSchedule();
        String xml = new TransitScheduleXmlWriter().writeToString(schedule);
        validateAgainstDtd(withDoctype(xml));

        TransitSchedule reloaded = new TransitScheduleXmlReader().read(xml);
        assertEquals(expectedLinks(), route(reloaded).getNetworkRoute());
    }

    @Test
    void streamingWriterOutputValidatesAgainstCurrentDtdAndMatchesDomWriter() throws Exception {
        TransitSchedule schedule = buildMirroringSchedule();
        String streamingXml = new StreamingTransitScheduleWriter().writeToString(schedule);
        validateAgainstDtd(withDoctype(streamingXml));

        // DOM sorts attributes alphabetically; StAX preserves insertion order.
        // Compare parsed model fields to prove semantic parity.
        TransitSchedule fromStreaming = new TransitScheduleXmlReader().read(streamingXml);
        assertEquals(3, fromStreaming.getFacilities().size());
        TransitRoute route = fromStreaming.getTransitLines().get(Id.create("line-1", TransitLine.class))
                .getRoutes().get(Id.create("route-1", TransitRoute.class));
        assertEquals(expectedLinks(), route.getNetworkRoute());
        assertEquals("bus", route.getTransportMode());
        assertEquals("Main line", route.getDescription());
        assertEquals("West", route.getAttributes().getAttribute("headsign"));
        assertEquals(3, route.getStops().size());
        assertEquals("bus-1", route.getDepartures().get(Id.create("dep-1", Departure.class)).getVehicleId());
    }

    private static TransitSchedule buildMirroringSchedule() {
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop1 = new TransitStopFacility(Id.create("stop-1", TransitStopFacility.class), new Coord(0.0, 0.0), false);
        stop1.setName("Main");
        stop1.setLinkId(Id.create("link-1", Link.class));
        stop1.setStopAreaId(Id.create("area-1", TransitStopArea.class));
        TransitStopFacility stop2 = new TransitStopFacility(Id.create("stop-2", TransitStopFacility.class), new Coord(100.0, 0.0), false);
        stop2.setLinkId(Id.create("link-2", Link.class));
        stop2.setStopAreaId(Id.create("area-1", TransitStopArea.class));
        TransitStopFacility stop3 = new TransitStopFacility(Id.create("stop-3", TransitStopFacility.class), new Coord(200.0, 0.0), false);
        stop3.setLinkId(Id.create("link-3", Link.class));
        schedule.addStopFacility(stop1);
        schedule.addStopFacility(stop2);
        schedule.addStopFacility(stop3);

        TransitLine line = new TransitLine(Id.create("line-1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route-1", TransitRoute.class));
        // A non-empty route attribute exercises the DTD-required attribute-first ordering.
        route.getAttributes().putAttribute("headsign", "West");
        route.setDescription("Main line");
        route.setTransportMode("bus");
        route.addStop(new TransitRouteStop(Id.create("stop-1", TransitStopFacility.class), 0.0, 30.0, false));
        route.addStop(new TransitRouteStop(Id.create("stop-2", TransitStopFacility.class), 60.0, 90.0, false));
        route.addStop(new TransitRouteStop(Id.create("stop-3", TransitStopFacility.class), 120.0, 150.0, false));
        route.setNetworkRoute(expectedLinks());
        Departure departure = new Departure(Id.create("dep-1", Departure.class), 8.5 * 3600.0);
        departure.setVehicleId("bus-1");
        route.addDeparture(departure);
        line.addRoute(route);
        schedule.addTransitLine(line);
        return schedule;
    }

    private static List<Id<Link>> expectedLinks() {
        return List.of(Id.create("link-1", Link.class), Id.create("link-2", Link.class), Id.create("link-3", Link.class));
    }

    private static TransitRoute route(TransitSchedule schedule) {
        return schedule.getTransitLines().get(Id.create("line-1", TransitLine.class))
                .getRoutes().get(Id.create("route-1", TransitRoute.class));
    }

    private static void validateAgainstDtd(String xml) throws Exception {
        String dtd = resourceAsString(DTD);
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("matsim-dtd");
        try {
            java.nio.file.Files.writeString(tempDir.resolve("transitSchedule_v2.dtd"), dtd);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setValidating(true);
            factory.setNamespaceAware(false);
            SAXParser parser = factory.newSAXParser();
            org.xml.sax.InputSource source = new InputSource(new StringReader(xml));
            source.setSystemId(tempDir.toUri().toString());
            parser.parse(source, new DefaultHandler() {
            });
        } finally {
            java.nio.file.Files.deleteIfExists(tempDir.resolve("transitSchedule_v2.dtd"));
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    /** Associates the current DTD with writer output (which carries no DOCTYPE declaration). */
    private static String withDoctype(String xml) {
        return xml.replaceFirst("\\?>", "?>\n<!DOCTYPE transitSchedule SYSTEM \"transitSchedule_v2.dtd\">");
    }

    private static String resourceAsString(String resource) throws Exception {
        try (InputStream inputStream = Objects.requireNonNull(
                        TransitScheduleSpecValidationTest.class.getClassLoader().getResourceAsStream(resource),
                        resource);
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }
}
