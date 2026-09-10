package com.citymodeler.matsim.models.gtfs;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolves which service dates are active for a given feed.
 */
public final class GtfsServiceSelector {

    private GtfsServiceSelector() {
    }

    public static Set<LocalDate> selectDates(GtfsFeed feed, GtfsImportConfig config) {
        GtfsImportConfig.ServiceDateSelection sel = config.serviceDateSelection();
        if (sel == GtfsImportConfig.ServiceDateSelection.ALL) {
            return allDates(feed);
        } else if (sel == GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS) {
            LocalDate d = dayWithMostTrips(feed);
            return d != null ? Set.of(d) : Set.of();
        } else if (sel == GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_SERVICES) {
            LocalDate d = dayWithMostServices(feed);
            return d != null ? Set.of(d) : Set.of();
        } else {
            if (config.explicitDate() == null) {
                throw new IllegalArgumentException("EXPLICIT service date selection requires explicitDate");
            }
            LocalDate date = parseDate(config.explicitDate());
            return date != null ? Set.of(date) : Set.of();
        }
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

    public static Set<LocalDate> activeDatesForService(String serviceId, GtfsFeed feed) {
        Set<LocalDate> dates = new TreeSet<>();
        for (GtfsCalendarRow row : feed.calendarRows()) {
            if (!row.serviceId().equals(serviceId)) continue;
            LocalDate start = parseDate(row.startDate());
            LocalDate end = parseDate(row.endDate());
            if (start == null || end == null) continue;
            int[] flags = row.weekdayFlags();
            LocalDate cursor = start;
            int safety = 0;
            while (!cursor.isAfter(end) && safety++ < 400) {
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
