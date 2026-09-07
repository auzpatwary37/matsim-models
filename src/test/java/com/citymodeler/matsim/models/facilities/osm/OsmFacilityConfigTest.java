package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OsmFacilityConfigTest {
    @Test
    void defaultsMapBusinessTagsToActivities() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        assertEquals("work", cfg.activityForType("office"));
        assertEquals("leisure", cfg.activityForType("restaurant"));
        assertEquals("education", cfg.activityForType("school"));
        assertEquals("health", cfg.activityForType("hospital"));
        assertEquals("work", cfg.activityForType("unknown_thing"));
    }

    @Test
    void defaultsIdentifyHouseholdsAndBusinesses() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        assertTrue(cfg.isHouseholdBuilding("house"));
        assertTrue(cfg.isHouseholdBuilding("apartments"));
        assertFalse(cfg.isHouseholdBuilding("retail"));
        assertTrue(cfg.isBusinessKey("amenity"));
        assertTrue(cfg.isBusinessKey("shop"));
        assertFalse(cfg.isBusinessKey("building"));
    }
}
