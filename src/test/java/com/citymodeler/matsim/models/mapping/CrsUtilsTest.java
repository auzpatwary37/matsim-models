package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;

/**
 * Review #1: the GTFS and OSM projection paths must share one explicit CRS contract. Two distinct
 * WGS84 stops must project to distinct coordinates in the network CRS (EPSG:3857), not collapse to
 * the origin.
 */
class CrsUtilsTest {

    @Test
    void twoDistinctWgs84StopsProjectToDistinctCoordinates() {
        Coord a = CrsUtils.projectWgs84("EPSG:3857", -73.560, 45.500);
        Coord b = CrsUtils.projectWgs84("EPSG:3857", -73.550, 45.500);

        // Same latitude, different longitude: X must differ (and the points are distinct).
        assertNotEquals(a.getX(), b.getX(), 1e-6);

        // ~1e-2 deg longitude at ~45.5N is roughly 700-900 m; the projection must not degenerate.
        double dx = Math.abs(a.getX() - b.getX());
        assertTrue(dx > 500.0 && dx < 1500.0, "expected ~km-scale x separation, got " + dx);

        // EPSG:3857 y for ~45.5N is on the order of millions of meters, not (0,0).
        assertTrue(Math.abs(a.getY()) > 5_000_000.0, "expected Mercator y in millions, got " + a.getY());
    }

    @Test
    void distinctLitudesProjectToDistinctY() {
        Coord a = CrsUtils.projectWgs84("EPSG:3857", -73.550, 45.500);
        Coord b = CrsUtils.projectWgs84("EPSG:3857", -73.550, 45.510);
        assertNotEquals(a.getY(), b.getY(), 1e-6);
        assertTrue(Math.abs(b.getY() - a.getY()) > 1000.0, "1e-2 deg lat should be ~1.1 km");
    }

    @Test
    void projectorDefaultsToNetworkCrsWhenAbsent() {
        assertEquals("EPSG:3857", CrsUtils.forCrs("EPSG:3857").targetCrs());
        assertEquals("EPSG:3857", CrsUtils.forCrs(null).targetCrs());
        assertEquals("EPSG:3857", CrsUtils.forCrs("").targetCrs());
    }

    @Test
    void samePointIsSelfConsistentAcrossCalls() {
        Coord p1 = CrsUtils.projectWgs84("EPSG:3857", -73.55, 45.50);
        Coord p2 = CrsUtils.forCrs("EPSG:3857").project(-73.55, 45.50);
        assertEquals(p1.getX(), p2.getX(), 1e-9);
        assertEquals(p1.getY(), p2.getY(), 1e-9);
    }
}
