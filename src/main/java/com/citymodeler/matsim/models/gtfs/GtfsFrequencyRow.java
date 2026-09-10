package com.citymodeler.matsim.models.gtfs;

/**
 * A row from frequencies.txt.
 */
public record GtfsFrequencyRow(
        String tripId,
        int startTime,
        int endTime,
        int headwaySecs,
        int exactTimes) {
}
