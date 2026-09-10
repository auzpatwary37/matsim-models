package com.citymodeler.matsim.models.gtfs;

import java.util.Objects;

/**
 * A GTFS route with agency and type information.
 */
public record GtfsRoute(
        String id,
        String agencyId,
        String shortName,
        String longName,
        String url,
        int routeType,
        String color,
        String textColor) {

    public int effectiveRouteType() {
        return routeType >= 0 ? routeType : 3; // default to bus
    }
}
