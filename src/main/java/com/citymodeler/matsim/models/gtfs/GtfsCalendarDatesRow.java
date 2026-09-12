package com.citymodeler.matsim.models.gtfs;

/**
 * A row from calendar_dates.txt. exception_type: 1=added, 2=removed.
 */
public record GtfsCalendarDatesRow(
        String serviceId,
        String date,
        int exceptionType) {
}
