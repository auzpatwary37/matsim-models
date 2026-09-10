package com.citymodeler.matsim.models.osm;

import java.util.List;
import java.util.Optional;

/**
 * Known Geofabrik PBF regions with approximate bounding boxes.
 * Used to resolve the smallest downloadable region containing a user's polygon.
 */
public final class GeofabrikRegions {

    private GeofabrikRegions() {
    }

    public record Region(String name, String url, OsmBoundary.BBox bbox) {
        public String baseName() {
            return name.replace("-latest", "");
        }
    }

    // Continental / large
    public static final Region NORTH_AMERICA = region("north-america",
            "https://download.geofabrik.de/north-america-latest.osm.pbf",
            -170, 15, -50, 75);
    public static final Region SOUTH_AMERICA = region("south-america",
            "https://download.geofabrik.de/south-america-latest.osm.pbf",
            -95, -60, -30, 15);
    public static final Region EUROPE = region("europe",
            "https://download.geofabrik.de/europe-latest.osm.pbf",
            -15, 34, 45, 72);
    public static final Region AFRICA = region("africa",
            "https://download.geofabrik.de/africa-latest.osm.pbf",
            -20, -38, 55, 38);
    public static final Region ASIA = region("asia",
            "https://download.geofabrik.de/asia-latest.osm.pbf",
            40, 0, 180, 75);
    public static final Region OCEANIA = region("oceania",
            "https://download.geofabrik.de/oceania-latest.osm.pbf",
            110, -50, 180, -8);

    // Countries
    public static final Region CANADA = region("canada",
            "https://download.geofabrik.de/north-america/canada-latest.osm.pbf",
            -142, 41, -52, 84);
    public static final Region USA = region("usa",
            "https://download.geofabrik.de/north-america/usa-latest.osm.pbf",
            -170, 24, -66, 50);
    public static final Region MEXICO = region("mexico",
            "https://download.geofabrik.de/north-america/mexico-latest.osm.pbf",
            -120, 14, -86, 33);
    public static final Region FRANCE = region("france",
            "https://download.geofabrik.de/europe/france-latest.osm.pbf",
            -5, 41, 10, 51);
    public static final Region GERMANY = region("germany",
            "https://download.geofabrik.de/europe/germany-latest.osm.pbf",
            5, 47, 15, 55);
    public static final Region UK = region("united-kingdom",
            "https://download.geofabrik.de/europe/united-kingdom-latest.osm.pbf",
            -11, 49, 2, 61);

    // Canadian provinces/states
    public static final Region QUEBEC = region("quebec",
            "https://download.geofabrik.de/north-america/canada/quebec-latest.osm.pbf",
            -79.5, 45, -52, 62);
    public static final Region ONTARIO = region("ontario",
            "https://download.geofabrik.de/north-america/canada/ontario-latest.osm.pbf",
            -95.5, 41.5, -74, 57);
    public static final Region BRITISH_COLUMBIA = region("british-columbia",
            "https://download.geofabrik.de/north-america/canada/british-columbia-latest.osm.pbf",
            -139, 48.5, -114, 60);
    public static final Region ALBERTA = region("alberta",
            "https://download.geofabrik.de/north-america/canada/alberta-latest.osm.pbf",
            -120, 49, -110, 60);
    public static final Region MANITOBA = region("manitoba",
            "https://download.geofabrik.de/north-america/canada/manitoba-latest.osm.pbf",
            -102, 49, -85, 60);
    public static final Region SASKATCHEWAN = region("saskatchewan",
            "https://download.geofabrik.de/north-america/canada/saskatchewan-latest.osm.pbf",
            -110, 49, -102, 60);
    public static final Region NOVA_SCOTIA = region("nova-scotia",
            "https://download.geofabrik.de/north-america/canada/nova-scotia-latest.osm.pbf",
            -66, 43, -59, 47.5);
    public static final Region NEW_BRUNSWICK = region("new-brunswick",
            "https://download.geofabrik.de/north-america/canada/new-brunswick-latest.osm.pbf",
            -68, 44.5, -64, 48);
    public static final Region NEWFOUNDLAND = region("newfoundland-and-labrador",
            "https://download.geofabrik.de/north-america/canada/newfoundland-and-labrador-latest.osm.pbf",
            -60, 46, -52, 60);

    // NOTE: Metro-level extracts are not guaranteed to exist on Geofabrik.
    // Resolution falls back to the containing province/state region.

    private static final List<Region> ALL = List.of(
            // Order matters: smallest first for resolution
            QUEBEC, ONTARIO, BRITISH_COLUMBIA, ALBERTA, MANITOBA, SASKATCHEWAN,
            NOVA_SCOTIA, NEW_BRUNSWICK, NEWFOUNDLAND,
            CANADA, USA, MEXICO, FRANCE, GERMANY, UK,
            NORTH_AMERICA, SOUTH_AMERICA, EUROPE, AFRICA, ASIA, OCEANIA
    );

    /**
     * Finds the smallest Geofabrik region whose bbox fully contains the given boundary.
     * Falls back to the continent if no smaller region fits.
     */
    public static Optional<Region> resolveSmallest(OsmBoundary boundary) {
        OsmBoundary.BBox target = boundary.boundingBox();
        for (Region region : ALL) {
            if (region.bbox().contains(target)) {
                return Optional.of(region);
            }
        }
        // Fallback: find a region that intersects (polygon spans multiple regions)
        for (Region region : ALL) {
            if (region.bbox().intersects(target)) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    /** Lists all known regions (for UI dropdowns or debugging). */
    public static List<Region> all() {
        return ALL;
    }

    private static Region region(String name, String url, double minLon, double minLat, double maxLon, double maxLat) {
        return new Region(name, url, new OsmBoundary.BBox(minLon, minLat, maxLon, maxLat));
    }
}
