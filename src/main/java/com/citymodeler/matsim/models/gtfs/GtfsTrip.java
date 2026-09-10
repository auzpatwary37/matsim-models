package com.citymodeler.matsim.models.gtfs;

import java.util.Objects;

/**
 * A GTFS trip with service and metadata.
 */
public record GtfsTrip(
        String id,
        String routeId,
        String serviceId,
        String tripHeadsign,
        String tripShortDescription,
        int directionId,
        boolean hasDirection,
        String blockId,
        String shapeId,
        boolean wheelchairAccessible,
        boolean bikesAllowed) {

    public int effectiveDirectionId() {
        return hasDirection ? directionId : 0;
    }
}
