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
            .filter(f -> "restaurant".equals(f.osmValue())).findFirst().orElseThrow();
        assertEquals("amenity", cafe.osmKey());
        assertEquals("Test Cafe", cafe.name());
        assertEquals("Mo-Fr 09:00-17:00", cafe.openingHours());
        assertEquals("Bathurst Street", cafe.address().get("addr:street"));
        assertTrue(!cafe.isHousehold());

        OsmFacility house = facilities.stream()
            .filter(OsmFacility::isHousehold).findFirst().orElseThrow();
        assertEquals("building", house.osmKey());
        assertEquals("house", house.osmValue());
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
        // amenity sorts before shop, so the selected key/value is the amenity's,
        // not the shop value that appears first in the file.
        assertEquals("amenity", facilities.get(0).osmKey());
        assertEquals("restaurant", facilities.get(0).osmValue());
    }

    @Test
    void classifiesBuildingWaysAndUseTagPoisByKey() throws Exception {
        Path fixture = Path.of(getClass().getClassLoader()
            .getResource("fixtures/osm-classification.osm").toURI());
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        List<OsmFacility> facilities = new OsmFacilityParser(cfg).parse(fixture);

        // 2 POI nodes + 6 building ways all survive; the 2 transit-infrastructure
        // amenity nodes (bus_station, taxi) are excluded, so the count is unchanged
        assertEquals(8, facilities.size());

        assertEquals("leisure", activity(cfg, byOsMValue(facilities, "hotel")));
        assertEquals("leisure", activity(cfg, byOsMValue(facilities, "fitness_centre")));
        assertEquals("work", activity(cfg, byOsMValue(facilities, "commercial")));
        assertEquals("work", activity(cfg, byOsMValue(facilities, "retail")));
        assertEquals("work", activity(cfg, byOsMValue(facilities, "industrial")));
        assertEquals("work", activity(cfg, byOsMValue(facilities, "office")));
        assertEquals("education", activity(cfg, byOsMValue(facilities, "school")));
        assertEquals("education", activity(cfg, byOsMValue(facilities, "university")));

        // building ways carry the building key, POIs carry their business key
        assertEquals("building", byOsMValue(facilities, "commercial").osmKey());
        assertEquals("tourism", byOsMValue(facilities, "hotel").osmKey());
        assertTrue(facilities.stream().noneMatch(OsmFacility::isHousehold));
        // transit/traffic infrastructure is dropped, not emitted as a facility
        assertTrue(facilities.stream().noneMatch(f -> "bus_station".equals(f.osmValue())));
        assertTrue(facilities.stream().noneMatch(f -> "taxi".equals(f.osmValue())));
    }

    @Test
    void emitsPolygonCentroidNotDoubleCountedVertexMean() throws Exception {
        Path fixture = Path.of(getClass().getClassLoader()
            .getResource("fixtures/osm-classification.osm").toURI());
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        List<OsmFacility> facilities = new OsmFacilityParser(cfg).parse(fixture);

        // way 200 is the closed triangle (refs 400,401,402,400). The repeated
        // closing ref must not double-weight the corner: the emitted point is the
        // centroid of the three unique vertices, not the mean of four refs.
        OsmFacility triangle = byOsMValue(facilities, "commercial");
        assertEquals((-79.40 - 79.39 - 79.40) / 3.0, triangle.lon(), 1e-9);
        assertEquals((43.60 + 43.60 + 43.61) / 3.0, triangle.lat(), 1e-9);
    }

    @Test
    void polygonCentroidIsAreaWeightedNotVertexMean() {
        // Asymmetric quadrilateral: the area-weighted (shoelace) centroid is
        // (56/36, 32/36) = (1.5556, 0.8889) in the ring's coordinate units,
        // whereas the plain vertex average is (1.5, 1.0).
        List<double[]> ring = List.of(
            new double[]{0.0, 0.0},
            new double[]{4.0, 0.0},
            new double[]{2.0, 2.0},
            new double[]{0.0, 2.0});
        double[] c = OsmFacilityParser.polygonCentroid(ring);
        assertEquals(1.5556, c[0], 0.01);
        assertEquals(0.8889, c[1], 0.01);

        // a degenerate (open/collinear) ring falls back to the vertex average
        List<double[]> open = List.of(new double[]{0.0, 0.0}, new double[]{1.0, 0.0});
        double[] o = OsmFacilityParser.polygonCentroid(open);
        assertEquals(0.5, o[0], 1e-9);
        assertEquals(0.0, o[1], 1e-9);
    }

    private static OsmFacility byOsMValue(List<OsmFacility> facilities, String value) {
        return facilities.stream()
            .filter(f -> value.equals(f.osmValue())).findFirst().orElseThrow();
    }

    private static String activity(OsmFacilityConfig cfg, OsmFacility f) {
        return cfg.activityFor(f.osmKey(), f.osmValue());
    }
}
