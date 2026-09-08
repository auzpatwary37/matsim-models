package com.citymodeler.matsim.models.facilities.osm;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for converting OSM facilities to MATSim facilities.
 *
 * <p>Activity classification is a function of the OSM {@code (key, value)} pair that
 * qualified a facility, not of the value alone: {@link #activityFor(String, String)}.
 * For use-tag businesses the value may override the key's default (e.g.
 * {@code amenity=school} is education while a bare {@code amenity=*} is work); for
 * {@code building=*} the value is matched against the building-type sets.
 *
 * @param targetCrs             target coordinate reference system (e.g. {@code EPSG:32617}).
 * @param personsPerSqm         persons per square metre used to estimate household capacity.
 * @param valueActivity         value-level activity overrides (e.g. {@code restaurant -> leisure}).
 * @param keyDefaultActivity    default activity per business key when the value has no override
 *                              (e.g. {@code tourism -> leisure}).
 * @param householdBuildingTypes OSM {@code building} values treated as households ({@code home}).
 * @param businessBuildingTypes  OSM {@code building} values that are commercial ({@code work}).
 * @param educationBuildingTypes OSM {@code building} values that are educational ({@code education}).
 * @param healthBuildingTypes    OSM {@code building} values that are health-related ({@code health}).
 * @param businessKeys          OSM tag keys that mark a facility as a business/POI.
 * @param excludedAmenityValues {@code amenity} values that are transit/traffic
 *                              infrastructure rather than facilities (e.g.
 *                              {@code bus_station}); these are skipped on qualification.
 */
public record OsmFacilityConfig(
        String targetCrs,
        double personsPerSqm,
        Map<String, String> valueActivity,
        Map<String, String> keyDefaultActivity,
        Set<String> householdBuildingTypes,
        Set<String> businessBuildingTypes,
        Set<String> educationBuildingTypes,
        Set<String> healthBuildingTypes,
        Set<String> businessKeys,
        Set<String> excludedAmenityValues) {

    /**
     * Maps the OSM {@code (key, value)} pair that classified a facility to a MATSim
     * activity type. The result depends on both the key and the value: {@code building}
     * values are matched against the building-type sets, and use-tag values may override
     * their key's default via {@code valueActivity}.
     */
    public String activityFor(String key, String value) {
        if ("building".equals(key)) {
            if (isResidentialBuilding(value)) {
                return "home";
            }
            if (isEducationBuilding(value)) {
                return "education";
            }
            if (isHealthBuilding(value)) {
                return "health";
            }
            return "work";
        }
        if (value != null && valueActivity.containsKey(value)) {
            return valueActivity.get(value);
        }
        return key != null ? keyDefaultActivity.getOrDefault(key, "work") : "work";
    }

    public boolean isResidentialBuilding(String buildingValue) {
        return buildingValue != null && householdBuildingTypes.contains(buildingValue);
    }

    public boolean isBusinessBuilding(String buildingValue) {
        return buildingValue != null && businessBuildingTypes.contains(buildingValue);
    }

    public boolean isEducationBuilding(String buildingValue) {
        return buildingValue != null && educationBuildingTypes.contains(buildingValue);
    }

    public boolean isHealthBuilding(String buildingValue) {
        return buildingValue != null && healthBuildingTypes.contains(buildingValue);
    }

    /**
     * True if a {@code building} value qualifies the element as a facility
     * (residential, commercial, educational, or health building).
     */
    public boolean isQualifyingBuilding(String buildingValue) {
        return isResidentialBuilding(buildingValue)
            || isBusinessBuilding(buildingValue)
            || isEducationBuilding(buildingValue)
            || isHealthBuilding(buildingValue);
    }

    public boolean isBusinessKey(String key) {
        return key != null && businessKeys.contains(key);
    }

    /**
     * True if a business POI is transit/traffic infrastructure rather than a
     * facility and should be skipped. Applies only to a set of {@code amenity}
     * values (bus/train/ferry stations, taxi stands); other keys are never
     * excluded.
     */
    public boolean isExcludedFacility(String key, String value) {
        return "amenity".equals(key)
            && value != null
            && excludedAmenityValues.contains(value);
    }

    /** Alias for {@link #isResidentialBuilding(String)} retained for clarity. */
    public boolean isHouseholdBuilding(String buildingValue) {
        return isResidentialBuilding(buildingValue);
    }

    public static OsmFacilityConfig defaults(String targetCrs) {
        return new OsmFacilityConfig(
            targetCrs,
            1.0 / 30.0,
            Map.ofEntries(
                Map.entry("restaurant", "leisure"),
                Map.entry("cafe", "leisure"),
                Map.entry("bar", "leisure"),
                Map.entry("fast_food", "leisure"),
                Map.entry("pub", "leisure"),
                Map.entry("attraction", "leisure"),
                Map.entry("school", "education"),
                Map.entry("university", "education"),
                Map.entry("college", "education"),
                Map.entry("kindergarten", "education"),
                Map.entry("hospital", "health"),
                Map.entry("clinic", "health"),
                Map.entry("pharmacy", "health"),
                Map.entry("doctors", "health"),
                Map.entry("dentist", "health"),
                Map.entry("bank", "work")),
            Map.of(
                "shop", "work",
                "office", "work",
                "tourism", "leisure",
                "leisure", "leisure",
                "amenity", "work"),
            Set.of("house", "detached", "semidetached_house", "terrace", "apartments", "residential"),
            Set.of("commercial", "retail", "industrial", "office"),
            Set.of("school", "university", "college"),
            Set.of("hospital", "clinic", "healthcare"),
            Set.of("amenity", "shop", "office", "tourism", "leisure"),
            Set.of("bus_station", "bus_stop", "tram_station", "railway_station",
                   "ferry_terminal", "taxi"));
    }
}
