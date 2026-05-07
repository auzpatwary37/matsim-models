package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
