package com.citymodeler.matsim.models.gtfs;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single GTFS stop. Only location_type 0 or empty are eligible for facility creation.
 */
public record GtfsStop(
        String id,
        String name,
        double lat,
        double lon,
        int locationType,
        String parentStationId,
        String zoneId,
        String stopUrl,
        String description) {

    public boolean isPlatform() {
        return locationType == 0;
    }

    public boolean hasCoordinates() {
        return !(Math.abs(lat) < 1e-9 && Math.abs(lon) < 1e-9);
    }
}
