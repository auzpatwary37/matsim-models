package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class OsmFacilityParserTest {
    @Test
    void parsesBusinessNodesAndHouseholdWays() throws Exception {
        Path fixture = Path.of(getClass().getClassLoader()
            .getResource("fixtures/osm-facilities.osm").toURI());
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        List<OsmFacility> facilities = new OsmFacilityParser(cfg).parse(fixture);

        // 2 business nodes + 1 household way
        assertEquals(3, facilities.size());

        OsmFacility cafe = facilities.stream()
            .filter(f -> f.type().equals("restaurant")).findFirst().orElseThrow();
        assertEquals("Test Cafe", cafe.name());
        assertEquals("Mo-Fr 09:00-17:00", cafe.openingHours());
        assertEquals("Bathurst Street", cafe.address().get("addr:street"));
        assertTrue(!cafe.isHousehold());

        OsmFacility house = facilities.stream()
            .filter(OsmFacility::isHousehold).findFirst().orElseThrow();
        assertEquals(2, house.levels());
        assertTrue(house.areaM2() > 0, "house footprint area should be computed");
    }

    @Test
    void parsesDistinctFacilitiesForCollidingIds() throws Exception {
        Path fixture = Path.of(getClass().getClassLoader()
            .getResource("fixtures/osm-id-collision.osm").toURI());
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        List<OsmFacility> facilities = new OsmFacilityParser(cfg).parse(fixture);

        // The business node id=5 and household way id=5 must survive as two
        // distinct facilities, disambiguated by the n/w id prefixes.
        assertEquals(2, facilities.size());
        Set<String> ids = facilities.stream().map(OsmFacility::id).collect(Collectors.toSet());
        assertEquals(Set.of("n5", "w5"), ids);
    }

    @Test
    void selectsBusinessTypeDeterministicallyBySortedKeys() throws Exception {
        Path fixture = Path.of(getClass().getClassLoader()
            .getResource("fixtures/osm-business-tags.osm").toURI());
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        List<OsmFacility> facilities = new OsmFacilityParser(cfg).parse(fixture);

        assertEquals(1, facilities.size());
        // amenity sorts before shop, so the selected type is the amenity value,
        // not the shop value that appears first in the file.
        assertEquals("restaurant", facilities.get(0).type());
    }
}
