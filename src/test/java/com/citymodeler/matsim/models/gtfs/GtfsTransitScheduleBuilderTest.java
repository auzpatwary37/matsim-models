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
        assertEquals("trolleybus", GtfsModeMapper.map(11));
        assertEquals("pt", GtfsModeMapper.map(12));      // monorail
        assertEquals("pt", GtfsModeMapper.map(99));      // unknown → pt
    }

    /** Review #11: non-standard route_types 8/9 are NOT known, so they trigger the unknown warning. */
    @Test
    void nonStandardRouteTypesAreNotKnown() {
        assertFalse(GtfsModeMapper.isKnown(8));
        assertFalse(GtfsModeMapper.isKnown(9));
        assertTrue(GtfsModeMapper.isKnown(7));
        assertTrue(GtfsModeMapper.isKnown(12));
    }

    private TransitRoute firstRoute(GtfsTransitBuildResult result) {
        for (TransitLine line : result.schedule().getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                return route;
            }
        }
        throw new AssertionError("expected at least one transit route");
    }

    private GtfsFeedSet feedWithFrequency(List<GtfsStopTime> stopTimes, List<GtfsFrequencyRow> freqs) {
        GtfsFeed feed = new GtfsFeed("feed1", "A1", "UTC",
                java.util.Map.of(
                        "S1", new GtfsStop("S1", "Stop One", 45.0, -73.0, 0, null, null, null, null),
                        "S2", new GtfsStop("S2", "Stop Two", 45.1, -73.1, 0, null, null, null, null)),
                java.util.Map.of(
                        "R1", new GtfsRoute("R1", "A1", "1", "Line 1", null, 3, null, null)),
                java.util.Map.of(
                        "T1", new GtfsTrip("T1", "R1", "SVC1", "Down", null, 0, true, null, null, false, false)),
                java.util.Map.of("T1", stopTimes),
                java.util.List.of(new GtfsCalendarRow("SVC1", 1, 1, 1, 1, 1, 0, 0, "20260101", "20260131")),
                java.util.List.of(), freqs, java.util.Map.of(), java.util.List.of());
        return new GtfsFeedSet(java.util.Map.of("feed1", feed), java.util.List.of());
    }

    /**
     * Review #8: a non-timepoint stop with no times must be linearly interpolated, and the FINAL
     * TransitRouteStop must carry that interpolated offset (not 0 / not the raw missing value).
     */
    @Test
    void interpolatedOffsetsReflectedInRouteStops() {
        GtfsFeed feed = new GtfsFeed("feed1", "A1", "UTC",
                java.util.Map.of(
                        "S1", new GtfsStop("S1", "A", 45.0, -73.0, 0, null, null, null, null),
                        "S2", new GtfsStop("S2", "B", 45.05, -73.0, 0, null, null, null, null),
                        "S3", new GtfsStop("S3", "C", 45.1, -73.0, 0, null, null, null, null)),
                java.util.Map.of(
                        "R1", new GtfsRoute("R1", "A1", "1", "Line 1", null, 3, null, null)),
                java.util.Map.of(
                        "T1", new GtfsTrip("T1", "R1", "SVC1", "Down", null, 0, true, null, null, false, false)),
                java.util.Map.of("T1", java.util.List.of(
                        new GtfsStopTime("T1", "S1", 1, 0, 0, true, 0, 0),
                        new GtfsStopTime("T1", "S2", 2, null, null, false, 0, 0),
                        new GtfsStopTime("T1", "S3", 3, 1200, 1200, true, 0, 0))),
                java.util.List.of(new GtfsCalendarRow("SVC1", 1, 1, 1, 1, 1, 0, 0, "20260101", "20260131")),
                java.util.List.of(), java.util.List.of(), java.util.Map.of(), java.util.List.of());
        GtfsFeedSet feedSet = new GtfsFeedSet(java.util.Map.of("feed1", feed), java.util.List.of());
        GtfsImportConfig config = GtfsImportConfig.forFolder(null,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsTransitBuildResult result = new GtfsTransitScheduleBuilder().build(feedSet, config);

        var stops = firstRoute(result).getStops();
        assertEquals(3, stops.size());
        // Middle stop S2 had no times; interpolated between S1(0) and S3(1200) -> 600.
        assertEquals(600.0, stops.get(1).getArrivalOffset(), 1e-6);
        assertEquals(600.0, stops.get(1).getDepartureOffset(), 1e-6);
        assertEquals(0.0, stops.get(0).getArrivalOffset(), 1e-6);
        assertEquals(1200.0, stops.get(2).getDepartureOffset(), 1e-6);
    }

    /** Review #10: the strictest frequency exact_times value is preserved on the route. */
    @Test
    void exactTimesPreservedInRouteMetadata() {
        GtfsFeedSet feedSet = feedWithFrequency(
                java.util.List.of(
                        new GtfsStopTime("T1", "S1", 1, 0, 0, true, 0, 0),
                        new GtfsStopTime("T1", "S2", 2, 120, 180, true, 0, 0)),
                java.util.List.of(new GtfsFrequencyRow("T1", 0, 86400, 600, 0)));
        GtfsImportConfig config = GtfsImportConfig.forFolder(null,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsTransitBuildResult result = new GtfsTransitScheduleBuilder().build(feedSet, config);

        Object exact = firstRoute(result).getAttributes().getAttribute("gtfs:exact_times");
        assertNotNull(exact, "exact_times metadata must be written when frequencies are present");
        assertEquals(0, ((Number) exact).intValue());
    }

    /** Review #10: overlapping frequency rows for one trip violate the GTFS contract and warn. */
    @Test
    void overlappingFrequencyRowsWarn() {
        GtfsFeedSet feedSet = feedWithFrequency(
                java.util.List.of(
                        new GtfsStopTime("T1", "S1", 1, 0, 0, true, 0, 0),
                        new GtfsStopTime("T1", "S2", 2, 120, 180, true, 0, 0)),
                java.util.List.of(
                        new GtfsFrequencyRow("T1", 0, 3600, 600, 1),
                        new GtfsFrequencyRow("T1", 100, 3500, 600, 1)));
        GtfsImportConfig config = GtfsImportConfig.forFolder(null,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsTransitBuildResult result = new GtfsTransitScheduleBuilder().build(feedSet, config);

        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("overlapping frequency rows")),
                "expected an overlapping-frequency warning, got " + result.warnings());
    }
}
