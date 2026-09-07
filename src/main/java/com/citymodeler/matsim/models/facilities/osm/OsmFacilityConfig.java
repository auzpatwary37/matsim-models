package com.citymodeler.matsim.models.facilities.osm;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for converting OSM facilities to MATSim facilities.
 *
 * @param targetCrs          target coordinate reference system (e.g. {@code EPSG:32617}).
 * @param personsPerSqm      persons per square metre used to estimate household capacity.
 * @param tagToActivity      maps OSM tag values to MATSim activity types.
 * @param householdBuildingTypes OSM {@code building} values treated as households.
 * @param businessKeys       OSM tag keys that mark a facility as a business/POI.
 */
public record OsmFacilityConfig(
        String targetCrs,
        double personsPerSqm,
        Map<String, String> tagToActivity,
        Set<String> householdBuildingTypes,
        Set<String> businessKeys) {

    public String activityForType(String type) {
        return tagToActivity.getOrDefault(type, "work");
    }

    public boolean isHouseholdBuilding(String buildingValue) {
        return householdBuildingTypes.contains(buildingValue);
    }

    public boolean isBusinessKey(String key) {
        return businessKeys.contains(key);
    }

    public static OsmFacilityConfig defaults(String targetCrs) {
        return new OsmFacilityConfig(
            targetCrs,
            1.0 / 30.0,
            Map.ofEntries(
                Map.entry("office", "work"),
                Map.entry("commercial", "work"),
                Map.entry("retail", "work"),
                Map.entry("industrial", "work"),
                Map.entry("restaurant", "leisure"),
                Map.entry("cafe", "leisure"),
                Map.entry("bar", "leisure"),
                Map.entry("fast_food", "leisure"),
                Map.entry("tourism", "leisure"),
                Map.entry("leisure", "leisure"),
                Map.entry("school", "education"),
                Map.entry("university", "education"),
                Map.entry("college", "education"),
                Map.entry("kindergarten", "education"),
                Map.entry("hospital", "health"),
                Map.entry("clinic", "health"),
                Map.entry("pharmacy", "health"),
                Map.entry("bank", "work"),
                Map.entry("shop", "work")),
            Set.of("house", "detached", "semidetached_house", "terrace", "apartments", "residential"),
            Set.of("amenity", "shop", "office", "tourism", "leisure"));
    }
}
