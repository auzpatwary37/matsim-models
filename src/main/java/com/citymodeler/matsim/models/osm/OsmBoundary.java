package com.citymodeler.matsim.models.osm;

import java.util.List;
import java.util.Objects;

/**
 * A closed polygon boundary for OSM data import. Vertices are [lon, lat] pairs in WGS84.
 * The polygon must be closed (first vertex == last vertex), non-self-intersecting,
 * and have positive area (counter-clockwise winding).
 */
public record OsmBoundary(List<double[]> vertices) {

    public OsmBoundary(List<double[]> vertices) {
        this.vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        if (this.vertices.size() < 4) {
            throw new IllegalArgumentException("Polygon requires at least 4 vertices (3 corners + closing point)");
        }
        if (!isClosed()) {
            throw new IllegalArgumentException("Polygon must be closed: first vertex must equal last vertex");
        }
    }

    public boolean isClosed() {
        double[] first = vertices.get(0);
        double[] last = vertices.get(vertices.size() - 1);
        return Math.abs(first[0] - last[0]) < 1e-9 && Math.abs(first[1] - last[1]) < 1e-9;
    }

    /**
     * Validates the polygon: no self-intersection and positive signed area.
     * Throws if invalid.
     */
    public OsmBoundary validate() {
        if (signedArea() <= 0) {
            throw new IllegalArgumentException("Polygon has zero or negative area (check vertex ordering — must be counter-clockwise)");
        }
        if (hasSelfIntersection()) {
            throw new IllegalArgumentException("Polygon has self-intersecting edges");
        }
        return this;
    }

    /** Signed area using the shoelace formula. Positive = CCW. */
    public double signedArea() {
        double area = 0;
        int n = vertices.size() - 1; // exclude closing duplicate
        for (int i = 0; i < n; i++) {
            double[] a = vertices.get(i);
            double[] b = vertices.get((i + 1) % n);
            area += (double) a[0] * b[1] - (double) b[0] * a[1];
        }
        return area / 2.0;
    }

    /** Checks if any non-adjacent edges intersect. */
    public boolean hasSelfIntersection() {
        int n = vertices.size() - 1;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // Skip adjacent edges (they share a vertex)
                if (i == 0 && j == n - 1) continue;
                if (j == i + 1) continue;
                if (edgesIntersect(vertices.get(i), vertices.get((i + 1) % n),
                        vertices.get(j), vertices.get((j + 1) % n))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Axis-aligned bounding box. */
    public record BBox(double minLon, double minLat, double maxLon, double maxLat) {
        public boolean contains(double lon, double lat) {
            return lon >= minLon && lon <= maxLon && lat >= minLat && lat <= maxLat;
        }
        public boolean contains(BBox other) {
            return other.minLon >= minLon && other.maxLon <= maxLon
                    && other.minLat >= minLat && other.maxLat <= maxLat;
        }
        public boolean intersects(BBox other) {
            return !(other.maxLon < minLon || other.minLon > maxLon
                    || other.maxLat < minLat || other.minLat > maxLat);
        }
    }

    public BBox boundingBox() {
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (double[] v : vertices) {
            if (v[0] < minLon) minLon = v[0];
            if (v[0] > maxLon) maxLon = v[0];
            if (v[1] < minLat) minLat = v[1];
            if (v[1] > maxLat) maxLat = v[1];
        }
        return new BBox(minLon, minLat, maxLon, maxLat);
    }

    /** Ray-casting point-in-polygon test. */
    public boolean contains(double lon, double lat) {
        BBox bbox = boundingBox();
        if (!bbox.contains(lon, lat)) return false;

        boolean inside = false;
        int n = vertices.size() - 1;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double[] vi = vertices.get(i);
            double[] vj = vertices.get(j);
            if ((vi[1] > lat) != (vj[1] > lat)) {
                double intersectLon = (vj[0] - vi[0]) * (lat - vi[1]) / (vj[1] - vi[1]) + vi[0];
                if (lon < intersectLon) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    static boolean edgesIntersect(double[] p1, double[] p2, double[] p3, double[] p4) {
        double d1 = direction(p3, p4, p1);
        double d2 = direction(p3, p4, p2);
        double d3 = direction(p1, p2, p3);
        double d4 = direction(p1, p2, p4);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        return false;
    }

    private static double direction(double[] a, double[] b, double[] c) {
        return (c[0] - a[0]) * (b[1] - a[1]) - (b[0] - a[0]) * (c[1] - a[1]);
    }
}
