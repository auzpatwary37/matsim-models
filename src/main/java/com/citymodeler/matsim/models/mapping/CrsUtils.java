package com.citymodeler.matsim.models.mapping;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.io.MatsimParseException;
import com.citymodeler.matsim.models.network.Network;

/**
 * Shared WGS84 (lon/lat) to projected-CRS projection contract.
 *
 * <p>Review #1: both the Phase-1 OSM importer and the GTFS stop-mapping layer must project into the
 * <em>same</em> CRS using the <em>same</em> pipeline, so that a stop and the network it is matched
 * against live in one coordinate space. This class owns that pipeline (proj4j {@code EPSG:4326} to
 * the network's target CRS) and both sides delegate to it. It never infers a projection from
 * coordinate magnitude; the target CRS is passed or read explicitly.
 */
public final class CrsUtils {

    /** The CRS the Phase-1 OSM network is built in by default. */
    public static final String DEFAULT_NETWORK_CRS = "EPSG:3857";

    /** Network attribute name under which the built network records its target CRS. */
    public static final String TARGET_CRS_ATTR = "osm:targetCrs";

    private CrsUtils() {
    }

    /**
     * Reads the target CRS recorded on a built network, falling back to the default when absent
     * (e.g. synthetic networks built for tests).
     */
    public static String networkTargetCrs(Network network) {
        Object value = network.getAttributes().getAttribute(TARGET_CRS_ATTR);
        return (value == null || value.toString().isBlank()) ? DEFAULT_NETWORK_CRS : value.toString();
    }

    /** Builds a projector from WGS84 into the given target CRS. */
    public static Projector forCrs(String targetCrs) {
        String crs = (targetCrs == null || targetCrs.isBlank()) ? DEFAULT_NETWORK_CRS : targetCrs;
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem wgs84 = crsFactory.createFromName("EPSG:4326");
        CoordinateReferenceSystem target = crsFactory.createFromName(crs);
        CoordinateTransform transform = new CoordinateTransformFactory().createTransform(wgs84, target);
        return new Projector(crs, transform);
    }

    /** Projects WGS84 coordinates into a specific target CRS. */
    public static Coord projectWgs84(String targetCrs, double lon, double lat) {
        return forCrs(targetCrs).project(lon, lat);
    }

    /** A reusable WGS84-to-target-CRS projector backed by a proj4j transform. */
    public static final class Projector {
        private final String targetCrs;
        private final CoordinateTransform transform;

        private Projector(String targetCrs, CoordinateTransform transform) {
            this.targetCrs = targetCrs;
            this.transform = transform;
        }

        public String targetCrs() {
            return targetCrs;
        }

        /** Projects a WGS84 (lon, lat) point into the target CRS (meters). */
        public Coord project(double lon, double lat) {
            ProjCoordinate src = new ProjCoordinate(lon, lat);
            ProjCoordinate dst = new ProjCoordinate();
            if (transform.transform(src, dst) == null) {
                throw new MatsimParseException(
                        "Failed to project WGS84 (" + lon + ", " + lat + ") into " + targetCrs);
            }
            return new Coord(dst.x, dst.y);
        }
    }
}
