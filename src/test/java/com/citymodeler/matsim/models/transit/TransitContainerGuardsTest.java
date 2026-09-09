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
    void stopFacilityParentIdRoundTripsThroughXml() {
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility parent = new TransitStopFacility(Id.create("parent-1", TransitStopFacility.class), new Coord(0, 0), false);
        TransitStopFacility child = new TransitStopFacility(Id.create("child-1", TransitStopFacility.class), new Coord(1, 1), false);
        child.setParentId(Id.create("parent-1", TransitStopFacility.class));
        schedule.addStopFacility(parent);
        schedule.addStopFacility(child);

        String xml = new TransitScheduleXmlWriter().writeToString(schedule);
        assertTrue(xml.contains("parentId=\"parent-1\""), xml);
        assertTrue(!xml.contains("parentId=\"parent-2\""), xml);

        TransitSchedule roundTripped = new TransitScheduleXmlReader().read(xml);
        assertEquals(Id.create("parent-1", TransitStopFacility.class),
                roundTripped.getFacilities().get(Id.create("child-1", TransitStopFacility.class)).getParentId());
        assertNull(roundTripped.getFacilities().get(Id.create("parent-1", TransitStopFacility.class)).getParentId());
    }
}
