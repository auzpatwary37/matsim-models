package com.citymodeler.matsim.models.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class TransitRoute {
    private final Id<TransitRoute> id;
    private String description;
    private String transportMode;
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

    public List<TransitRouteStop> getStops() {
        return Collections.unmodifiableList(stops);
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
        departures.put(departure.getId(), departure);
    }
}
