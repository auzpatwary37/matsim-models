package com.citymodeler.matsim.models.gtfs;

/**
 * A GTFS stop_time entry. Times are seconds from midnight (may exceed 86400).
 */
public record GtfsStopTime(
        String tripId,
        String stopId,
        int stopSequence,
        Integer arrivalTime,
        Integer departureTime,
        boolean isTimepoint,
        int pickupType,
        int dropOffType) {

    public boolean hasArrival() {
        return arrivalTime != null;
    }

    public boolean hasDeparture() {
        return departureTime != null;
    }
}
