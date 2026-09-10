package com.citymodeler.matsim.models.gtfs;

import java.util.List;
import java.util.Map;

/**
 * Result of building a transit schedule from GTFS feeds.
 */
public record GtfsTransitBuildResult(
        com.citymodeler.matsim.models.transit.TransitSchedule schedule,
        List<VehicleDef> vehicles,
        Map<String, List<SelectedDate>> selectedDatesByFeed,
        List<String> warnings) {

    public record VehicleDef(String vehicleId, String vehicleType, int capacity, int seats) {
    }

    public record SelectedDate(String feedId, java.time.LocalDate date) {
    }
}
