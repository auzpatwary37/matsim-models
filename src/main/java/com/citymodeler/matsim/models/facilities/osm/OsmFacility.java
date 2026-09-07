package com.citymodeler.matsim.models.facilities.osm;

import java.util.Map;

/**
 * A single facility parsed from OSM, before conversion to a MATSim
 * {@link com.citymodeler.matsim.models.facilities.ActivityFacility}.
 *
 * @param id            facility id: the OSM element id prefixed with {@code "n"} (node)
 *                      or {@code "w"} (way) so colliding node/way ids stay distinct.
 * @param lon           longitude in WGS84 decimal degrees.
 * @param lat           latitude in WGS84 decimal degrees.
 * @param name          OSM {@code name} tag, may be null.
 * @param osmKey        OSM tag key that classified the facility: a business key
 *                      ({@code amenity}/{@code shop}/{@code office}/{@code tourism}/{@code
 *                      leisure}) or {@code building}.
 * @param osmValue      value of that key (e.g. {@code restaurant}, {@code hotel},
 *                      {@code commercial}, {@code house}). Together with {@code osmKey} it
 *                      drives the MATSim activity classification.
 * @param openingHours  OSM {@code opening_hours} tag, may be null.
 * @param address       map of {@code addr:*} tags (street, housenumber, city, postcode).
 * @param areaM2        building footprint area in square metres (0 if unknown).
 * @param levels        number of building levels (0 if unknown).
 * @param isHousehold   true if this is a residential building (home facility).
 */
public record OsmFacility(
        String id,
        double lon,
        double lat,
        String name,
        String osmKey,
        String osmValue,
        String openingHours,
        Map<String, String> address,
        double areaM2,
        int levels,
        boolean isHousehold) {
}
