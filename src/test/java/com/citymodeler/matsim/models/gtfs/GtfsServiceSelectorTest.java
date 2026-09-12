package com.citymodeler.matsim.models.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Review regressions for service materialization: no arbitrary 400-day truncation (#2) and the
 * {@code assumeAlwaysActive} contract for feeds without calendar data (#3).
 */
class GtfsServiceSelectorTest {

    private GtfsFeed feedWithCalendar(GtfsCalendarRow... rows) {
        return new GtfsFeed("feed1", "A1", "UTC",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(rows),
                List.of(), List.of(), Map.of(), List.of());
    }

    private GtfsFeed feedWithoutCalendar() {
        return new GtfsFeed("feed1", "A1", "UTC",
                Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), List.of(), List.of(), Map.of(), List.of());
    }

    private GtfsImportConfig config(GtfsImportConfig.ServiceDateSelection sel, boolean assumeAlways) {
        return new GtfsImportConfig(List.of(), sel, assumeAlways, null);
    }

    /** Review #2: a calendar spanning more than 400 days is materialized in full. */
    @Test
    void longCalendarIsNotTruncated() {
        // Every day active, 2025-01-01 through 2026-06-30 (546 days).
        GtfsCalendarRow row = new GtfsCalendarRow("SVC", 1, 1, 1, 1, 1, 1, 1, "20250101", "20260630");
        GtfsFeed feed = feedWithCalendar(row);

        Set<LocalDate> dates = GtfsServiceSelector.activeDatesForService("SVC", feed);

        assertEquals(546, dates.size(), "all 546 days must be materialized");
        // A date near the declared end must be present (the old cap cut at ~day 400).
        assertTrue(dates.contains(LocalDate.of(2026, 6, 30)), "declared end date must survive");
        assertTrue(dates.contains(LocalDate.of(2026, 1, 1)), "date past the old 400-day cap must survive");
    }

    /** Review #3: no calendar + assumeAlwaysActive=false is fatal. */
    @Test
    void noCalendarWithoutAssumeAlwaysActiveIsFatal() {
        GtfsServiceSelector.ServiceSelection sel = GtfsServiceSelector.select(
                feedWithoutCalendar(), config(GtfsImportConfig.ServiceDateSelection.ALL, false));
        assertTrue(sel.hasFatal(), "missing calendar data must be fatal when assumeAlwaysActive=false");
        assertTrue(sel.dates().isEmpty());
    }

    /** Review #3: no calendar + assumeAlwaysActive=true yields a non-empty date set and a warning. */
    @Test
    void noCalendarWithAssumeAlwaysActiveProducesDatesAndWarning() {
        GtfsServiceSelector.ServiceSelection sel = GtfsServiceSelector.select(
                feedWithoutCalendar(), config(GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS, true));
        assertFalse(sel.hasFatal());
        assertFalse(sel.dates().isEmpty(), "assumeAlwaysActive must yield an active date");
        assertTrue(sel.warnings().stream().anyMatch(w -> w.contains("assumeAlwaysActive")),
                "assumeAlwaysActive must be recorded as a warning");
    }

    /** Review #3: EXPLICIT date with assumeAlwaysActive=true uses the explicit date. */
    @Test
    void explicitDateWithAssumeAlwaysActiveUsesExplicitDate() {
        GtfsImportConfig cfg = new GtfsImportConfig(List.of(),
                GtfsImportConfig.ServiceDateSelection.EXPLICIT, true, "20260315");
        GtfsServiceSelector.ServiceSelection sel = GtfsServiceSelector.select(feedWithoutCalendar(), cfg);
        assertFalse(sel.hasFatal());
        assertEquals(Set.of(LocalDate.of(2026, 3, 15)), sel.dates());
    }

    /** The builder raises a fatal exception for a no-calendar feed unless assumeAlwaysActive. */
    @Test
    void builderThrowsForNoCalendarFeed() {
        GtfsFeed feed = feedWithoutCalendar();
        GtfsFeedSet set = new GtfsFeedSet(Map.of("feed1", feed), List.of());
        GtfsImportConfig cfg = GtfsImportConfig.forFolder(null,
                GtfsImportConfig.ServiceDateSelection.ALL);
        assertThrows(GtfsImportException.class,
                () -> new GtfsTransitScheduleBuilder().build(set, cfg));
    }

    /**
     * Review #1 END-TO-END: a no-calendar feed with assumeAlwaysActive=true must actually produce a
     * schedule with departures (not merely select a date). The route must exist and its departures
     * must belong to the expected trip.
     */
    @Test
    void assumeAlwaysActiveProducesDeparturesEndToEnd() {
        GtfsFeed feed = new GtfsFeed("feed1", "A1", "UTC",
                Map.of("S1", new GtfsStop("S1", "Stop One", 45.0, -73.0, 0, null, null, null, null),
                        "S2", new GtfsStop("S2", "Stop Two", 45.1, -73.1, 0, null, null, null, null)),
                Map.of("R1", new GtfsRoute("R1", "A1", "1", "Line 1", null, 3, null, null)),
                Map.of("T1", new GtfsTrip("T1", "R1", "SVC1", "Down", null, 0, true, null, null, false, false)),
                Map.of("T1", List.of(
                        new GtfsStopTime("T1", "S1", 1, 28800, 28800, true, 0, 0),
                        new GtfsStopTime("T1", "S2", 2, 28920, 28980, true, 0, 0))),
                List.of(), List.of(), List.of(), Map.of(), List.of()); // no calendar rows
        GtfsFeedSet set = new GtfsFeedSet(Map.of("feed1", feed), List.of());

        GtfsImportConfig cfg = new GtfsImportConfig(
                List.of(new GtfsImportConfig.FeedSource(null, "feed1")),
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS, true);
        GtfsTransitBuildResult result = new GtfsTransitScheduleBuilder().build(set, cfg);

        assertFalse(result.schedule().getTransitLines().isEmpty(), "a route must exist");
        int departures = 0;
        for (var line : result.schedule().getTransitLines().values()) {
            for (var route : line.getRoutes().values()) {
                departures += route.getDepartures().size();
            }
        }
        assertTrue(departures > 0, "assumeAlwaysActive must materialize departures, not zero");
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("assumeAlwaysActive")));
        // selected date is deterministic and present
        assertFalse(result.selectedDatesByFeed().get("feed1").isEmpty());
    }

    /** Review #2: end < start is reported explicitly, not silently yielding empty service. */
    @Test
    void calendarEndBeforeStartIsReported() {
        GtfsCalendarRow row = new GtfsCalendarRow("SVC", 1, 1, 1, 1, 1, 1, 1, "20260601", "20260101");
        GtfsServiceSelector.ServiceSelection sel = GtfsServiceSelector.select(
                feedWithCalendar(row), config(GtfsImportConfig.ServiceDateSelection.ALL, false));
        assertTrue(sel.warnings().stream().anyMatch(w -> w.contains("SVC") && w.contains("before")),
                "end-before-start must be reported");
        assertTrue(sel.dates().isEmpty());
    }

    /** Review #2: a span beyond MAX_CALENDAR_DAYS is reported explicitly, not silently dropped. */
    @Test
    void excessiveCalendarSpanIsReported() {
        GtfsCalendarRow row = new GtfsCalendarRow("SVC", 1, 1, 1, 1, 1, 1, 1, "19000101", "21000101");
        GtfsServiceSelector.ServiceSelection sel = GtfsServiceSelector.select(
                feedWithCalendar(row), config(GtfsImportConfig.ServiceDateSelection.ALL, false));
        assertTrue(sel.warnings().stream().anyMatch(w -> w.contains("exceeds")),
                "over-limit span must be reported");
    }
}
