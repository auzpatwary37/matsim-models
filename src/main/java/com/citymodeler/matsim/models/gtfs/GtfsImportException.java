package com.citymodeler.matsim.models.gtfs;

/**
 * Thrown when a GTFS feed cannot be imported or materialized because of a fatal data problem
 * (for example a feed with neither {@code calendar.txt} nor {@code calendar_dates.txt} while
 * {@code assumeAlwaysActive} is disabled).
 */
public class GtfsImportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GtfsImportException(String message) {
        super(message);
    }

    public GtfsImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
