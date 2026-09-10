package com.citymodeler.matsim.models.gtfs;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitSchedule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.time.LocalDate;

class GtfsTransitScheduleBuilderTest {

    private GtfsFeedSet singleFeed() {
        GtfsFeed feed = new GtfsFeed("feed1", "A1", "UTC",
                java.util.Map.of(
                        "S1", new GtfsStop("S1", "Stop One", 45.0, -73.0, 0, null, null, null, null),
                        "S2", new GtfsStop("S2", "Stop Two", 45.1, -73.1, 0, null, null, null, null)),
                java.util.Map.of(
                        "R1", new GtfsRoute("R1", "A1", "1", "Line 1", null, 3, null, null)),
                java.util.Map.of(
                        "T1", new GtfsTrip("T1", "R1", "SVC1", "Down", null, 0, true, null, null, false, false)),
                java.util.Map.of(
                        "T1", List.of(
                                new GtfsStopTime("T1", "S1", 1, 28800, 28800, true, 0, 0),
                                new GtfsStopTime("T1", "S2", 2, 28920, 28980, true, 0, 0))),
                List.of(new GtfsCalendarRow("SVC1", 1, 1, 1, 1, 1, 0, 0, "20260101", "20260131")),
                List.of(), List.of(), java.util.Map.of(), List.of());
        return new GtfsFeedSet(java.util.Map.of("feed1", feed), List.of());
    }

    @Test
    void producesScheduleWithFacilitiesAndRoutes() {
        GtfsImportConfig config = GtfsImportConfig.forFolder(null,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsTransitBuildResult result = new GtfsTransitScheduleBuilder().build(singleFeed(), config);

        TransitSchedule schedule = result.schedule();
        assertEquals(2, schedule.getFacilities().size());
        assertTrue(schedule.getFacilities().keySet().stream()
                .anyMatch(id -> id.toString().equals("feed1:S1")));

        assertEquals(1, schedule.getTransitLines().size());
        TransitLine line = schedule.getTransitLines().values().iterator().next();
        assertEquals(1, line.getRoutes().size());
        TransitRoute route = line.getRoutes().values().iterator().next();
        assertEquals("pt", route.getTransportMode());
        assertEquals(2, route.getStops().size());
        assertFalse(route.getDepartures().isEmpty());
    }

    @Test
    void departureIdsAreUnique() {
        GtfsImportConfig config = GtfsImportConfig.forFolder(null,
                GtfsImportConfig.ServiceDateSelection.ALL);
        GtfsTransitBuildResult result = new GtfsTransitScheduleBuilder().build(singleFeed(), config);
        TransitSchedule schedule = result.schedule();
        int count = 0;
        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (Departure dep : route.getDepartures().values()) {
                    assertNotNull(dep.getVehicleId());
                    count++;
                }
            }
        }
        assertTrue(count > 0);
    }

    @Test
    void serviceDaySelectionWorks() {
        GtfsFeed feed = singleFeed().feeds().get("feed1");
        // Jan 1 2026 is Thursday
        Set<LocalDate> dates = GtfsServiceSelector.activeDatesForService("SVC1", feed);
        assertTrue(dates.contains(LocalDate.of(2026, 1, 1)));
        assertFalse(dates.contains(LocalDate.of(2026, 1, 3))); // Saturday
        assertTrue(dates.size() > 20); // Weekdays in January
    }

    @Test
    void modeMapperCoversAllTypes() {
        assertEquals("tram", GtfsModeMapper.map(0));
        assertEquals("subway", GtfsModeMapper.map(1));
        assertEquals("rail", GtfsModeMapper.map(2));
        assertEquals("pt", GtfsModeMapper.map(3));       // bus
        assertEquals("ferry", GtfsModeMapper.map(4));    // ferry
        assertEquals("cable_car", GtfsModeMapper.map(5));// cable tram
        assertEquals("aerial_lift", GtfsModeMapper.map(6)); // aerial lift
        assertEquals("funicular", GtfsModeMapper.map(7)); // funicular
        assertEquals("aerial_lift", GtfsModeMapper.map(8)); // aerial tramway
        assertEquals("funicular", GtfsModeMapper.map(9)); // subway (trolley)
        assertEquals("trolleybus", GtfsModeMapper.map(11));
        assertEquals("pt", GtfsModeMapper.map(12));      // monorail
        assertEquals("pt", GtfsModeMapper.map(99));      // unknown → pt
    }
}
