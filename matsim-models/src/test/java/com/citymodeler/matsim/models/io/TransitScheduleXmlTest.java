package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

class TransitScheduleXmlTest {
    @Test
    void readsWritesAndReadsTransitScheduleXml() {
        String xml = """
                <transitSchedule>
                    <attributes>
                        <attribute name=\"schedule-kind\" class=\"java.lang.String\">weekday</attribute>
                    </attributes>
                    <transitStops>
                        <stopFacility id=\"stop-1\" x=\"1.0\" y=\"2.0\" linkId=\"l1\" name=\"First Stop\" isBlocking=\"true\">
                            <attributes>
                                <attribute name=\"stop-zone\" class=\"java.lang.String\">A</attribute>
                            </attributes>
                        </stopFacility>
                    </transitStops>
                    <transitLine id=\"line-1\" name=\"Line 1\">
                        <attributes>
                            <attribute name=\"line-kind\" class=\"java.lang.String\">rapid</attribute>
                        </attributes>
                        <transitRoute id=\"route-1\">
                            <description>Main route</description>
                            <transportMode>bus</transportMode>
                            <routeProfile>
                                <stop refId=\"stop-1\" arrivalOffset=\"0.0\" departureOffset=\"30.0\" awaitDeparture=\"true\" />
                            </routeProfile>
                            <departures>
                                <departure id=\"dep-1\" departureTime=\"3600.0\" vehicleRefId=\"veh-1\" />
                            </departures>
                        </transitRoute>
                    </transitLine>
                </transitSchedule>
                """;

        TransitSchedule schedule = new TransitScheduleXmlReader().read(xml);
        String roundTrippedXml = new TransitScheduleXmlWriter().writeToString(schedule);
        TransitSchedule roundTripped = new TransitScheduleXmlReader().read(roundTrippedXml);

        assertEquals("weekday", roundTripped.getAttributes().getAttribute("schedule-kind"));
        assertEquals(1, roundTripped.getFacilities().size());
        assertEquals(1, roundTripped.getTransitLines().size());

        TransitStopFacility facility = roundTripped.getFacilities().get(Id.create("stop-1", TransitStopFacility.class));
        assertEquals("First Stop", facility.getName());
        assertEquals("A", facility.getAttributes().getAttribute("stop-zone"));

        TransitLine line = roundTripped.getTransitLines().get(Id.create("line-1", TransitLine.class));
        assertEquals("rapid", line.getAttributes().getAttribute("line-kind"));
        TransitRoute route = line.getRoutes().get(Id.create("route-1", TransitRoute.class));
        assertEquals("Main route", route.getDescription());
        assertEquals("bus", route.getTransportMode());
        TransitRouteStop stop = route.getStops().get(0);
        assertEquals(0.0, stop.getArrivalOffset());
        assertEquals(30.0, stop.getDepartureOffset());
        assertEquals(true, stop.isAwaitDeparture());
        assertEquals(facility, stop.getStopFacility());
        assertNotNull(stop.getStopFacility());

        Departure departure = route.getDepartures().get(Id.create("dep-1", Departure.class));
        assertEquals(3600.0, departure.getDepartureTime());
        assertEquals("veh-1", departure.getVehicleId());
    }

    @Test
    void loadFromClasspathFixture() {
        String fixturePath = "fixtures/transitSchedule.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        TransitSchedule schedule = new TransitScheduleXmlReader().read(is);

        assertNotNull(schedule);
        assertEquals(3, schedule.getFacilities().size());
        assertEquals(2, schedule.getTransitLines().size());
        assertEquals("weekday-commute", schedule.getAttributes().getAttribute("schedule-kind"));

        TransitStopFacility stop1 = schedule.getFacilities().get(Id.create("stop-1", TransitStopFacility.class));
        assertEquals("Home Station", stop1.getName());

        TransitLine line1 = schedule.getTransitLines().get(Id.create("line-1", TransitLine.class));
        TransitRoute route1 = line1.getRoutes().get(Id.create("route-1", TransitRoute.class));
        assertEquals(2, route1.getStops().size());
        assertEquals("bus", route1.getTransportMode());
    }

    @Test
    void transportModeAsAttribute_parsedCorrectly() {
        String xml = """
                <transitSchedule>
                    <transitLine id="line-1">
                        <transitRoute id="route-1" transportMode="tram">
                        </transitRoute>
                    </transitLine>
                </transitSchedule>
                """;
        TransitSchedule schedule = new TransitScheduleXmlReader().read(xml);
        TransitLine line = schedule.getTransitLines().get(Id.create("line-1", TransitLine.class));
        TransitRoute route = line.getRoutes().get(Id.create("route-1", TransitRoute.class));
        assertEquals("tram", route.getTransportMode());
    }

    @Test
    void transportModeAsChildElement_parsedCorrectly() {
        String xml = """
                <transitSchedule>
                    <transitLine id="line-1">
                        <transitRoute id="route-1">
                            <transportMode>bus</transportMode>
                        </transitRoute>
                    </transitLine>
                </transitSchedule>
                """;
        TransitSchedule schedule = new TransitScheduleXmlReader().read(xml);
        TransitLine line = schedule.getTransitLines().get(Id.create("line-1", TransitLine.class));
        TransitRoute route = line.getRoutes().get(Id.create("route-1", TransitRoute.class));
        assertEquals("bus", route.getTransportMode());
    }

    @Test
    void writer_usesTransportModeAttribute() {
        String xml = """
                <transitSchedule>
                    <transitLine id="line-1">
                        <transitRoute id="route-1" transportMode="bus">
                        </transitRoute>
                    </transitLine>
                </transitSchedule>
                """;
        TransitSchedule schedule = new TransitScheduleXmlReader().read(xml);
        String output = new TransitScheduleXmlWriter().writeToString(schedule);
        assertTrue(output.contains("transportMode=\"bus\""));
        assertFalse(output.contains("<transportMode>bus</transportMode>"));
    }
}
