package com.citymodeler.matsim.models.transit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class TransitSchedule {
    private final Map<Id<TransitStopFacility>, TransitStopFacility> facilities = new LinkedHashMap<>();
    private final Map<Id<TransitLine>, TransitLine> transitLines = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public Map<Id<TransitStopFacility>, TransitStopFacility> getFacilities() {
        return Collections.unmodifiableMap(facilities);
    }

    public Map<Id<TransitLine>, TransitLine> getTransitLines() {
        return Collections.unmodifiableMap(transitLines);
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void addStopFacility(TransitStopFacility stopFacility) {
        Objects.requireNonNull(stopFacility, "stopFacility");
        if (facilities.containsKey(stopFacility.getId())) {
            throw new IllegalArgumentException("Duplicate stop facility id: " + stopFacility.getId());
        }
        facilities.put(stopFacility.getId(), stopFacility);
    }

    public void addTransitLine(TransitLine transitLine) {
        Objects.requireNonNull(transitLine, "transitLine");
        if (transitLines.containsKey(transitLine.getId())) {
            throw new IllegalArgumentException("Duplicate transit line id: " + transitLine.getId());
        }
        transitLines.put(transitLine.getId(), transitLine);
    }

    public void postProcess() {
        for (TransitLine line : transitLines.values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (TransitRouteStop stop : route.getStops()) {
                    stop.clearStopFacility();
                }
            }
        }

        for (TransitLine line : transitLines.values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (TransitRouteStop stop : route.getStops()) {
                    TransitStopFacility stopFacility = facilities.get(stop.getStopFacilityId());
                    if (stopFacility == null) {
                        throw new IllegalStateException("Missing transit stop facility: " + stop.getStopFacilityId());
                    }
                    stop.setStopFacility(stopFacility);
                }
            }
        }
    }
}
