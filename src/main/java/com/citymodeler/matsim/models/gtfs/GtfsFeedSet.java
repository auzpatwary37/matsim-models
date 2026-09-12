package com.citymodeler.matsim.models.gtfs;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Container for one or more GTFS feeds with query views.
 */
public final class GtfsFeedSet {

    private final Map<String, GtfsFeed> feeds;
    private final List<String> warnings;

    public GtfsFeedSet(Map<String, GtfsFeed> feeds, List<String> warnings) {
        this.feeds = Collections.unmodifiableMap(new LinkedHashMap<>(feeds));
        this.warnings = List.copyOf(warnings);
    }

    public Map<String, GtfsFeed> feeds() { return feeds; }
    public Collection<GtfsFeed> allFeeds() { return feeds.values(); }
    public GtfsFeed feed(String feedId) { return feeds.get(feedId); }
    public List<String> warnings() { return warnings; }

    /** All stops across all feeds, keyed by prefixed ID. */
    public Map<String, GtfsStop> allStops() {
        Map<String, GtfsStop> result = new LinkedHashMap<>();
        for (GtfsFeed feed : feeds.values()) {
            feed.stops().forEach((id, stop) -> result.put(feed.prefixedStopId(id), stop));
        }
        return Collections.unmodifiableMap(result);
    }

    /** All routes across all feeds, keyed by prefixed ID. */
    public Map<String, GtfsRoute> allRoutes() {
        Map<String, GtfsRoute> result = new LinkedHashMap<>();
        for (GtfsFeed feed : feeds.values()) {
            feed.routes().forEach((id, route) -> result.put(feed.prefixedRouteId(id), route));
        }
        return Collections.unmodifiableMap(result);
    }

    /** All trips across all feeds, keyed by prefixed ID. */
    public Map<String, GtfsTrip> allTrips() {
        Map<String, GtfsTrip> result = new LinkedHashMap<>();
        for (GtfsFeed feed : feeds.values()) {
            feed.trips().forEach((id, trip) -> result.put(feed.prefixedTripId(id), trip));
        }
        return Collections.unmodifiableMap(result);
    }
}
