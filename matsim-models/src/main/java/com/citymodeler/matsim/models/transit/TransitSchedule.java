package com.citymodeler.matsim.models.transit;

import java.util.LinkedHashMap;
import java.util.Map;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class TransitSchedule {
    private final Map<Id<TransitStopFacility>, TransitStopFacility> facilities = new LinkedHashMap<>();
    private final Map<Id<TransitLine>, TransitLine> transitLines = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public Map<Id<TransitStopFacility>, TransitStopFacility> getFacilities() {
        return facilities;
    }

    public Map<Id<TransitLine>, TransitLine> getTransitLines() {
        return transitLines;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void addStopFacility(TransitStopFacility stopFacility) {
        facilities.put(stopFacility.getId(), stopFacility);
    }

    public void addTransitLine(TransitLine transitLine) {
        transitLines.put(transitLine.getId(), transitLine);
    }

    public void postProcess() {
        for (TransitLine line : transitLines.values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (TransitRouteStop stop : route.getStops()) {
                    stop.clearStopFacility();
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
