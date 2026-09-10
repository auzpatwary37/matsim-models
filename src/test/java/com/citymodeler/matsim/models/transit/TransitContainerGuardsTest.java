package com.citymodeler.matsim.models.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.TransitScheduleXmlReader;
import com.citymodeler.matsim.models.io.TransitScheduleXmlWriter;

class TransitContainerGuardsTest {

    @Test
    void scheduleRejectsDuplicateStopFacilities() {
        TransitSchedule schedule = new TransitSchedule();
        schedule.addStopFacility(new TransitStopFacility(Id.create("s1", TransitStopFacility.class), new Coord(0, 0), false));
        assertThrows(IllegalArgumentException.class,
                () -> schedule.addStopFacility(new TransitStopFacility(Id.create("s1", TransitStopFacility.class), new Coord(0, 0), false)));
    }

    @Test
    void scheduleRejectsDuplicateTransitLines() {
        TransitSchedule schedule = new TransitSchedule();
        schedule.addTransitLine(new TransitLine(Id.create("l1", TransitLine.class)));
        assertThrows(IllegalArgumentException.class,
                () -> schedule.addTransitLine(new TransitLine(Id.create("l1", TransitLine.class))));
    }

    @Test
    void lineRejectsDuplicateRoutes() {
        TransitLine line = new TransitLine(Id.create("l1", TransitLine.class));
        line.addRoute(new TransitRoute(Id.create("r1", TransitRoute.class)));
        assertThrows(IllegalArgumentException.class,
                () -> line.addRoute(new TransitRoute(Id.create("r1", TransitRoute.class))));
    }

    @Test
    void routeRejectsDuplicateDepartures() {
        TransitRoute route = new TransitRoute(Id.create("r1", TransitRoute.class));
        route.addDeparture(new Departure(Id.create("d1", Departure.class), 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> route.addDeparture(new Departure(Id.create("d1", Departure.class), 60.0)));
    }

    @Test
    void stopFacilityStopAreaIdRoundTripsThroughXml() {
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility sibling = new TransitStopFacility(Id.create("stop-1", TransitStopFacility.class), new Coord(0, 0), false);
        TransitStopFacility stop = new TransitStopFacility(Id.create("stop-2", TransitStopFacility.class), new Coord(1, 1), false);
        stop.setStopAreaId(Id.create("area-1", TransitStopArea.class));
        schedule.addStopFacility(sibling);
        schedule.addStopFacility(stop);

        String xml = new TransitScheduleXmlWriter().writeToString(schedule);
        assertTrue(xml.contains("stopAreaId=\"area-1\""), xml);
        assertTrue(!xml.contains("parentId="), xml);

        TransitSchedule roundTripped = new TransitScheduleXmlReader().read(xml);
        assertEquals(Id.create("area-1", TransitStopArea.class),
                roundTripped.getFacilities().get(Id.create("stop-2", TransitStopFacility.class)).getStopAreaId());
        assertNull(roundTripped.getFacilities().get(Id.create("stop-1", TransitStopFacility.class)).getStopAreaId());
    }

    @Test
    void readerAcceptsHistoricParentIdAliasAsStopAreaId() {
        String xml = """
                <transitSchedule>
                  <transitStops>
                    <stopFacility id="s1" x="0.0" y="0.0" parentId="area-1" isBlocking="false"/>
                  </transitStops>
                </transitSchedule>
                """;

        TransitSchedule schedule = new TransitScheduleXmlReader().read(xml);

        assertEquals(Id.create("area-1", TransitStopArea.class),
                schedule.getFacilities().get(Id.create("s1", TransitStopFacility.class)).getStopAreaId());
    }
}
