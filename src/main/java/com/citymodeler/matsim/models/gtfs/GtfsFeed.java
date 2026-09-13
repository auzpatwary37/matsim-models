package com.citymodeler.matsim.models.gtfs;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A single parsed GTFS feed with all its entities.
 */
public final class GtfsFeed {

    private final String feedId;
    private final String agencyId;
    private final String agencyTimezone;
    private final Map<String, GtfsStop> stops;
    private final Map<String, GtfsRoute> routes;
    private final Map<String, GtfsTrip> trips;
    private final Map<String, List<GtfsStopTime>> stopTimesByTrip;
    private final List<GtfsCalendarRow> calendarRows;
    private final List<GtfsCalendarDatesRow> calendarDatesRows;
    private final List<GtfsFrequencyRow> frequencyRows;
    private final Map<String, List<GtfsShapePoint>> shapesByShapeId;
    private final List<String> warnings;

    public GtfsFeed(String feedId, String agencyId, String agencyTimezone,
                    Map<String, GtfsStop> stops, Map<String, GtfsRoute> routes,
                    Map<String, GtfsTrip> trips, Map<String, List<GtfsStopTime>> stopTimesByTrip,
                    List<GtfsCalendarRow> calendarRows, List<GtfsCalendarDatesRow> calendarDatesRows,
                    List<GtfsFrequencyRow> frequencyRows, Map<String, List<GtfsShapePoint>> shapesByShapeId,
                    List<String> warnings) {
        this.feedId = Objects.requireNonNull(feedId, "feedId");
        this.agencyId = agencyId;
        this.agencyTimezone = agencyTimezone;
        this.stops = canonicalMap(stops);
        this.routes = canonicalMap(routes);
        this.trips = canonicalMap(trips);
        this.stopTimesByTrip = canonicalMap(stopTimesByTrip);
        this.calendarRows = List.copyOf(calendarRows);
        this.calendarDatesRows = List.copyOf(calendarDatesRows);
        this.frequencyRows = List.copyOf(frequencyRows);
        this.shapesByShapeId = canonicalMap(shapesByShapeId);
        this.warnings = List.copyOf(warnings);
    }

    /**
     * Wraps a map in an unmodifiable view with a canonical key order (natural String ordering).
     * Two reasons: (1) {@code Map.copyOf} returns a JDK immutable map whose iteration order is
     * salted per JVM run, so the schedule builder's route numbering (which walks these maps) became
     * process-dependent; (2) preserving the CSV physical row order would instead make output depend
     * on row order, so shuffling a feed's rows would change it. Canonical ordering is invariant to
     * both, keeping built schedules byte-stable across runs and row permutations.
     */
    private static <V> Map<String, V> canonicalMap(Map<String, V> source) {
        return Collections.unmodifiableMap(new TreeMap<>(source));
    }

    public String feedId() { return feedId; }
    public String agencyId() { return agencyId; }
    public String agencyTimezone() { return agencyTimezone; }
    public Map<String, GtfsStop> stops() { return stops; }
    public Map<String, GtfsRoute> routes() { return routes; }
    public Map<String, GtfsTrip> trips() { return trips; }
    public Map<String, List<GtfsStopTime>> stopTimesByTrip() { return stopTimesByTrip; }
    public List<GtfsCalendarRow> calendarRows() { return calendarRows; }
    public List<GtfsCalendarDatesRow> calendarDatesRows() { return calendarDatesRows; }
    public List<GtfsFrequencyRow> frequencyRows() { return frequencyRows; }
    public Map<String, List<GtfsShapePoint>> shapesByShapeId() { return shapesByShapeId; }
    public List<String> warnings() { return warnings; }

    public String prefixedStopId(String stopId) { return feedId + ":" + stopId; }
    public String prefixedRouteId(String routeId) { return feedId + ":" + routeId; }
    public String prefixedTripId(String tripId) { return feedId + ":" + tripId; }
}
