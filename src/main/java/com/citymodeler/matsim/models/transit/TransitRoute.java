package com.citymodeler.matsim.models.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

public final class TransitRoute {
    private final Id<TransitRoute> id;
    private String description;
    private String transportMode;
    private List<Id<Link>> networkRoute;
    private final List<TransitRouteStop> stops = new ArrayList<>();
    private final Map<Id<Departure>, Departure> departures = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public TransitRoute(Id<TransitRoute> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<TransitRoute> getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    /** Ordered network link sequence; null until mapped. */
    public List<Id<Link>> getNetworkRoute() {
        return networkRoute == null ? null : List.copyOf(networkRoute);
    }

    public void setNetworkRoute(List<Id<Link>> networkRoute) {
        if (networkRoute == null) {
            this.networkRoute = null;
            return;
        }
        if (networkRoute.isEmpty()) {
            throw new IllegalArgumentException("networkRoute must not be empty; pass null to clear");
        }
        for (Id<Link> linkId : networkRoute) {
            if (linkId == null) {
                throw new IllegalArgumentException("networkRoute must not contain null link ids");
            }
        }
        this.networkRoute = List.copyOf(networkRoute);
    }

    public List<TransitRouteStop> getStops() {
        return Collections.unmodifiableList(stops);
    }

    /**
     * Replaces the route's ordered stop list. Used by the network mapper to rewire stops to their
     * per-(parent, link) child facilities after mapping (review #2).
     */
    public void setStops(List<TransitRouteStop> newStops) {
        if (newStops == null) {
            throw new IllegalArgumentException("newStops must not be null; pass an empty list to clear");
        }
        stops.clear();
        for (TransitRouteStop stop : newStops) {
            Objects.requireNonNull(stop, "stop");
            stops.add(stop);
        }
    }

    public Map<Id<Departure>, Departure> getDepartures() {
        return Collections.unmodifiableMap(departures);
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void addStop(TransitRouteStop stop) {
        Objects.requireNonNull(stop, "stop");
        stops.add(stop);
    }

    public void addDeparture(Departure departure) {
        Objects.requireNonNull(departure, "departure");
        if (departures.containsKey(departure.getId())) {
            throw new IllegalArgumentException("Duplicate departure id: " + departure.getId());
        }
        departures.put(departure.getId(), departure);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransitRoute that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
