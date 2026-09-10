package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Coord;

/**
 * Coordinate reference system utilities for transforming between WGS84 (lon/lat)
 * and the projected CRS used by the Phase-1 OSM network.
 */
public final class CrsUtils {

    private CrsUtils() {
    }

    /**
     * Transforms WGS84 (lon, lat) to a local projected coordinate system centered
     * at the given reference point. Uses a simple equirectangular projection
     * (sufficient for city-scale models, ~50km).
     *
     * @param lon  WGS84 longitude in degrees
     * @param lat  WGS84 latitude in degrees
     * @param refLon reference longitude (center of projection)
     * @param refLat reference latitude (center of projection)
     * @return projected coordinates in meters
     */
    public static Coord wgs84ToProjected(double lon, double lat, double refLon, double refLat) {
        double cosLat = Math.cos(Math.toRadians(refLat));
        double metersPerDegLon = 111320.0 * cosLat;
        double metersPerDegLat = 110574.0;
        double x = (lon - refLon) * metersPerDegLon;
        double y = (lat - refLat) * metersPerDegLat;
        return new Coord(x, y);
    }

    /**
     * Determines the reference point (center) of a set of WGS84 coordinates.
     * Uses the centroid of the min/max bounding box.
     */
    public static double[] wgs84Center(double[] lons, double[] lats) {
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (int i = 0; i < lons.length; i++) {
            minLon = Math.min(minLon, lons[i]);
            maxLon = Math.max(maxLon, lons[i]);
            minLat = Math.min(minLat, lats[i]);
            maxLat = Math.max(maxLat, lats[i]);
        }
        return new double[]{(minLon + maxLon) / 2.0, (minLat + maxLat) / 2.0};
    }
}
