package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OsmFacilityConfigTest {
    @Test
    void buildingValuesClassifyToExpectedActivities() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        // commercial / educational / residential building values
        assertEquals("work", cfg.activityFor("building", "commercial"));
        assertEquals("work", cfg.activityFor("building", "retail"));
        assertEquals("work", cfg.activityFor("building", "industrial"));
        assertEquals("work", cfg.activityFor("building", "office"));
        assertEquals("education", cfg.activityFor("building", "school"));
        assertEquals("education", cfg.activityFor("building", "university"));
        assertEquals("home", cfg.activityFor("building", "house"));
        assertEquals("home", cfg.activityFor("building", "apartments"));
    }

    @Test
    void useTagCategoriesAreKeyAware() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        // key-driven defaults (the value is a subtype, not a top-level category)
        assertEquals("leisure", cfg.activityFor("tourism", "hotel"));
        assertEquals("leisure", cfg.activityFor("tourism", "museum"));
        assertEquals("leisure", cfg.activityFor("leisure", "fitness_centre"));
        assertEquals("work", cfg.activityFor("shop", "bakery"));
        assertEquals("work", cfg.activityFor("office", "company"));
        // value-level overrides on a work-default key
        assertEquals("leisure", cfg.activityFor("amenity", "restaurant"));
        assertEquals("education", cfg.activityFor("amenity", "school"));
        assertEquals("health", cfg.activityFor("amenity", "hospital"));
        assertEquals("work", cfg.activityFor("amenity", "bank"));
        // unknown (key, value) falls back to the key default, then to work
        assertEquals("work", cfg.activityFor("amenity", "unknown_thing"));
    }

    @Test
    void identifiesHouseholdBusinessEducationAndHealthBuildings() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        assertTrue(cfg.isResidentialBuilding("house"));
        assertTrue(cfg.isResidentialBuilding("apartments"));
        assertTrue(cfg.isBusinessBuilding("commercial"));
        assertTrue(cfg.isBusinessBuilding("retail"));
        assertTrue(cfg.isEducationBuilding("university"));
        assertTrue(cfg.isHealthBuilding("hospital"));
        assertTrue(cfg.isQualifyingBuilding("commercial"));
        assertTrue(cfg.isQualifyingBuilding("house"));
        assertFalse(cfg.isQualifyingBuilding("shed"));
        assertTrue(cfg.isBusinessKey("amenity"));
        assertTrue(cfg.isBusinessKey("shop"));
        assertFalse(cfg.isBusinessKey("building"));
    }

    @Test
    void predicatesAreNullTolerant() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        assertFalse(cfg.isResidentialBuilding(null));
        assertFalse(cfg.isBusinessBuilding(null));
        assertFalse(cfg.isEducationBuilding(null));
        assertFalse(cfg.isHealthBuilding(null));
        assertFalse(cfg.isQualifyingBuilding(null));
        assertFalse(cfg.isBusinessKey(null));
        // unknown building key value falls back to work rather than throwing
        assertEquals("work", cfg.activityFor("building", null));
        assertEquals("work", cfg.activityFor(null, null));
    }

    @Test
    void excludesTransitInfrastructureAmenities() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        assertTrue(cfg.isExcludedFacility("amenity", "bus_station"));
        assertTrue(cfg.isExcludedFacility("amenity", "taxi"));
        assertTrue(cfg.isExcludedFacility("amenity", "railway_station"));
        // other amenity values are real facilities
        assertFalse(cfg.isExcludedFacility("amenity", "restaurant"));
        // exclusion only applies to the amenity key
        assertFalse(cfg.isExcludedFacility("shop", "bus_station"));
        assertFalse(cfg.isExcludedFacility(null, null));
    }
}
