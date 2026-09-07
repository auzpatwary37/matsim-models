package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;

class OsmFacilityConverterTest {
    @Test
    void convertsBusinessToWorkFacilityWithAddress() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        OsmFacilityConverter converter = new OsmFacilityConverter(cfg);
        OsmFacility cafe = new OsmFacility(
            "1", -79.4, 43.6, "Test Cafe", "restaurant",
            "Mo-Fr 09:00-17:00",
            Map.of("addr:street", "Bathurst Street", "addr:housenumber", "100"),
            0.0, 0, false);

        ActivityFacilities facilities = converter.convert(List.of(cafe));
        ActivityFacility f = facilities.getFacilities().values().iterator().next();

        assertEquals("leisure", f.getActivityOptions().get("leisure").getType());
        assertEquals("Mo-Fr 09:00-17:00", f.getAttributes().getAttribute("opening_hours"));
        assertEquals("Bathurst Street", f.getAttributes().getAttribute("addr:street"));
        // reprojected to UTM 17N: x should be positive, y large
        assertTrue(f.getCoord().getX() > 0);
        assertTrue(f.getCoord().getY() > 1000000);
    }

    @Test
    void convertsHouseholdToHomeFacilityWithCapacity() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        OsmFacilityConverter converter = new OsmFacilityConverter(cfg);
        OsmFacility house = new OsmFacility(
            "10", -79.4, 43.6, null, "house", null,
            Map.of(), 300.0, 2, true);

        ActivityFacilities facilities = converter.convert(List.of(house));
        ActivityFacility f = facilities.getFacilities().values().iterator().next();

        assertEquals("home", f.getActivityOptions().get("home").getType());
        double expected = OsmFacilityConverter.estimateHouseholdCapacity(300.0, 2, cfg.personsPerSqm());
        assertEquals(expected, f.getActivityOptions().get("home").getCapacity(), 0.001);
        assertTrue(expected > 0);
    }

    @Test
    void estimateHouseholdCapacityUsesAreaTimesLevels() {
        double cap = OsmFacilityConverter.estimateHouseholdCapacity(300.0, 2, 1.0 / 30.0);
        assertEquals(20.0, cap, 0.001); // 300 * 2 / 30
    }
}
