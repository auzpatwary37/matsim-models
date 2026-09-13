package com.citymodeler.matsim.models.gtfs;

import java.util.List;
import java.util.Map;

import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;

/**
 * Result of building a transit schedule from GTFS feeds.
 *
 * <p>Per the Phase 2 design, the builder produces both a {@link com.citymodeler.matsim.models.transit.TransitSchedule}
 * and {@link VehicleDefinitions}: one {@link com.citymodeler.matsim.models.vehicles.VehicleType} per
 * resulting transit mode, and one {@link com.citymodeler.matsim.models.vehicles.Vehicle} per
 * departure. Every departure's {@code vehicleRefId} resolves to a declared vehicle, and every vehicle
 * references a declared type.
 */
public record GtfsTransitBuildResult(
        com.citymodeler.matsim.models.transit.TransitSchedule schedule,
        VehicleDefinitions vehicles,
        Map<String, List<SelectedDate>> selectedDatesByFeed,
        List<String> warnings) {

    public record SelectedDate(String feedId, java.time.LocalDate date) {
    }
}
