package com.citymodeler.matsim.models.gtfs;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolves which service dates are active for a given feed.
 *
 * <p>Calendar materialization follows the Phase-2 contract: {@code calendar.txt} weekday/date-range
 * rules first, then {@code calendar_dates.txt} exceptions (1 = added, 2 = removed). A feed with
 * neither calendar file is fatal unless {@link GtfsImportConfig#assumeAlwaysActive()} is set, in
 * which case all trips are treated as active for every queried date and a warning is recorded.
 */
public final class GtfsServiceSelector {

    /** Upper bound on a defensible single calendar span (~100 years); longer is treated as invalid. */
    static final long MAX_CALENDAR_DAYS = 36600L;

    private GtfsServiceSelector() {
    }

    /**
     * Result of resolving active dates for a feed: the dates plus any fatal/warning messages.
     * {@code fatal} is non-empty only when the feed has no calendar data and
     * {@code assumeAlwaysActive} is false.
     */
    public record ServiceSelection(Set<LocalDate> dates, List<String> warnings, List<String> fatal) {
        public boolean hasFatal() {
            return !fatal.isEmpty();
        }
    }

    public static Set<LocalDate> selectDates(GtfsFeed feed, GtfsImportConfig config) {
        return select(feed, config).dates();
    }

    /**
     * Resolve active dates for a feed, applying the configured selection mode and the
     * {@code assumeAlwaysActive} contract for feeds without calendar data.
     */
    public static ServiceSelection select(GtfsFeed feed, GtfsImportConfig config) {
        List<String> warnings = new ArrayList<>();
        List<String> fatal = new ArrayList<>();

        boolean noCalendar = feed.calendarRows().isEmpty() && feed.calendarDatesRows().isEmpty();
        if (noCalendar) {
            if (!config.assumeAlwaysActive()) {
                fatal.add(feed.feedId() + ": neither calendar.txt nor calendar_dates.txt present; "
                        + "enable assumeAlwaysActive to treat all trips as always active");
                return new ServiceSelection(Set.of(), warnings, fatal);
            }
            LocalDate anchor = anchorDate(feed, config);
            warnings.add(feed.feedId() + ": no calendar data; assumeAlwaysActive=true, treating all "
                    + "trips as active on " + anchor);
            return new ServiceSelection(Set.of(anchor), warnings, fatal);
        }

        Set<LocalDate> dates = switch (config.serviceDateSelection()) {
            case ALL -> allDates(feed);
            case DAY_WITH_MOST_TRIPS -> {
                LocalDate d = dayWithMostTrips(feed);
                yield d != null ? Set.of(d) : Set.of();
            }
            case DAY_WITH_MOST_SERVICES -> {
                LocalDate d = dayWithMostServices(feed);
                yield d != null ? Set.of(d) : Set.of();
            }
            case EXPLICIT -> {
                if (config.explicitDate() == null) {
                    throw new IllegalArgumentException("EXPLICIT service date selection requires explicitDate");
                }
                LocalDate date = parseDate(config.explicitDate());
                yield date != null ? Set.of(date) : Set.of();
            }
        };
        // Surface any structurally invalid calendar rows (span/ordering) as warnings (Review #2).
        List<String> calendarDiagnostics = new ArrayList<>();
        for (GtfsCalendarRow row : feed.calendarRows()) {
            activeDatesForService(row.serviceId(), feed, calendarDiagnostics);
        }
        for (String d : new java.util.LinkedHashSet<>(calendarDiagnostics)) {
            warnings.add(feed.feedId() + ": " + d);
        }
        return new ServiceSelection(dates, warnings, fatal);
    }

    /**
     * The single date used for an always-active feed: the explicit date when one is configured,
     * otherwise a stable sentinel (epoch) so output is deterministic regardless of the wall clock.
     */
    static LocalDate anchorDate(GtfsFeed feed, GtfsImportConfig config) {
        if (config.explicitDate() != null) {
            LocalDate d = parseDate(config.explicitDate());
            if (d != null) {
                return d;
            }
        }
        return LocalDate.EPOCH;
    }


    public static Set<LocalDate> allDates(GtfsFeed feed) {
        Set<LocalDate> result = new TreeSet<>();
        for (GtfsCalendarRow row : feed.calendarRows()) {
            result.addAll(activeDatesForService(row.serviceId(), feed));
        }
        // Also handle services that only appear in calendar_dates
        for (GtfsCalendarDatesRow ex : feed.calendarDatesRows()) {
            if (ex.exceptionType() == 1) {
                LocalDate date = parseDate(ex.date());
                if (date != null) result.add(date);
            }
        }
        return result;
    }

    public static LocalDate dayWithMostServices(GtfsFeed feed) {
        Map<LocalDate, Integer> count = new HashMap<>();
        for (GtfsCalendarRow row : feed.calendarRows()) {
            Set<LocalDate> dates = activeDatesForService(row.serviceId(), feed);
            for (LocalDate d : dates) {
                count.merge(d, 1, Integer::sum);
            }
        }
        if (count.isEmpty()) return null;
        return count.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public static LocalDate dayWithMostTrips(GtfsFeed feed) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (GtfsTrip trip : feed.trips().values()) {
            String serviceId = trip.serviceId();
            if (serviceId == null) continue;
            Set<LocalDate> dates = activeDatesForService(serviceId, feed);
            for (LocalDate date : dates) {
                long count = 1;
                for (GtfsFrequencyRow f : feed.frequencyRows()) {
                    if (trip.id().equals(f.tripId()) && f.headwaySecs() > 0) {
                        count += (f.endTime() - f.startTime()) / f.headwaySecs();
                    }
                }
                counts.merge(date, count, Long::sum);
            }
        }
        if (counts.isEmpty()) {
            return dayWithMostServices(feed);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Single source of truth for "is the given service active on the given date?", used both by the
     * date-selection step and the downstream departure loop so the two never disagree (Review #1).
     *
     * <p>An always-active feed (no calendar data with {@code assumeAlwaysActive=true}) treats every
     * service as active on every selected date. Otherwise the normal calendar/calendar_dates rules
     * apply.
     */
    public static boolean serviceActiveOnDate(GtfsFeed feed, String serviceId, LocalDate date,
                                              GtfsImportConfig config) {
        boolean noCalendar = feed.calendarRows().isEmpty() && feed.calendarDatesRows().isEmpty();
        if (noCalendar) {
            return config.assumeAlwaysActive();
        }
        if (serviceId == null) {
            return true;
        }
        return activeDatesForService(serviceId, feed).contains(date);
    }

    public static Set<LocalDate> activeDatesForService(String serviceId, GtfsFeed feed) {
        return activeDatesForService(serviceId, feed, null);
    }

    /**
     * Materialize active dates for a service. When {@code diagnostics} is supplied, structurally
     * invalid calendar rows (end before start, or a declared span beyond {@link #MAX_CALENDAR_DAYS})
     * are reported explicitly instead of being silently dropped (Review #2).
     */
    public static Set<LocalDate> activeDatesForService(String serviceId, GtfsFeed feed,
                                                       List<String> diagnostics) {
        Set<LocalDate> dates = new TreeSet<>();
        for (GtfsCalendarRow row : feed.calendarRows()) {
            if (!row.serviceId().equals(serviceId)) continue;
            LocalDate start = parseDate(row.startDate());
            LocalDate end = parseDate(row.endDate());
            if (start == null || end == null) continue;
            if (end.isBefore(start)) {
                if (diagnostics != null) {
                    diagnostics.add("service " + serviceId + ": calendar end date " + row.endDate()
                            + " is before start date " + row.startDate() + "; ignoring row");
                }
                continue;
            }
            // No arbitrary truncation: a valid calendar must be materialized in full. A declared
            // span beyond the guard is a data-integrity problem and is reported, never silently lost.
            if (start.until(end, java.time.temporal.ChronoUnit.DAYS) > MAX_CALENDAR_DAYS) {
                if (diagnostics != null) {
                    diagnostics.add("service " + serviceId + ": calendar span " + row.startDate()
                            + ".." + row.endDate() + " exceeds " + MAX_CALENDAR_DAYS
                            + " days; ignoring row");
                }
                continue;
            }
            int[] flags = row.weekdayFlags();
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                int idx = cursor.getDayOfWeek().getValue() - 1;
                if (idx >= 0 && idx < 7 && flags[idx] == 1) {
                    dates.add(cursor);
                }
                cursor = cursor.plusDays(1);
            }
        }
        for (GtfsCalendarDatesRow ex : feed.calendarDatesRows()) {
            if (!ex.serviceId().equals(serviceId)) continue;
            LocalDate date = parseDate(ex.date());
            if (date == null) continue;
            if (ex.exceptionType() == 1) {
                dates.add(date);
            } else if (ex.exceptionType() == 2) {
                dates.remove(date);
            }
        }
        return dates;
    }

    static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.of(
                    Integer.parseInt(s.substring(0, 4)),
                    Integer.parseInt(s.substring(4, 6)),
                    Integer.parseInt(s.substring(6, 8)));
        } catch (Exception e) {
            return null;
        }
    }
}
