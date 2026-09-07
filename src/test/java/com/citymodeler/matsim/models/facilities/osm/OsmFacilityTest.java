package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class OsmFacilityTest {
    @Test
    void recordExposesParsedFields() {
        OsmFacility f = new OsmFacility(
            "123", -79.4, 43.6, "Randolph Theatre", "theatre",
            "Mo-Fr 10:00-18:00",
            Map.of("addr:street", "Bathurst Street", "addr:housenumber", "736"),
            250.0, 2, false);
        assertEquals("123", f.id());
        assertEquals(-79.4, f.lon());
        assertEquals(43.6, f.lat());
        assertEquals("Randolph Theatre", f.name());
        assertEquals("theatre", f.type());
        assertEquals("Mo-Fr 10:00-18:00", f.openingHours());
        assertEquals("Bathurst Street", f.address().get("addr:street"));
        assertEquals(250.0, f.areaM2());
        assertEquals(2, f.levels());
        assertTrue(!f.isHousehold());
    }
}
