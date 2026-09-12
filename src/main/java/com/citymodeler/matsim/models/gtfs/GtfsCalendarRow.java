package com.citymodeler.matsim.models.gtfs;

/**
 * A row from calendar.txt.
 */
public record GtfsCalendarRow(
        String serviceId,
        int monday, int tuesday, int wednesday,
        int thursday, int friday, int saturday, int sunday,
        String startDate,
        String endDate) {

    public int[] weekdayFlags() {
        return new int[]{monday, tuesday, wednesday, thursday, friday, saturday, sunday};
    }
}
