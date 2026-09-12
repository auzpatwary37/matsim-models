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
}
