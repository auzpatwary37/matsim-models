package com.citymodeler.matsim.models.gtfs;

/**
 * A row from agency.txt.
 */
public record GtfsAgency(
        String id,
        String name,
        String url,
        String timezone,
        String phone,
        String fareUrl,
        String lang) {
}
