package com.citymodeler.matsim.models.facilities.osm;

import java.util.List;
import java.util.Map;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.facilities.ActivityOption;

/**
 * Converts parsed {@link OsmFacility} records into MATSim
 * {@link ActivityFacilities}, reprojecting coordinates from WGS84 to the
 * configured target CRS using proj4j.
 */
public final class OsmFacilityConverter {

    private final OsmFacilityConfig config;
    private final CoordinateTransform transform;

    public OsmFacilityConverter(OsmFacilityConfig config) {
        this.config = config;
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem wgs84 = crsFactory.createFromName("EPSG:4326");
        CoordinateReferenceSystem target = crsFactory.createFromName(config.targetCrs());
        this.transform = new CoordinateTransformFactory().createTransform(wgs84, target);
    }

    public ActivityFacilities convert(List<OsmFacility> facilities) {
        ActivityFacilities result = new ActivityFacilities("osm-facilities");
        for (OsmFacility osm : facilities) {
            result.addFacility(toActivityFacility(osm));
        }
        return result;
    }

    private ActivityFacility toActivityFacility(OsmFacility osm) {
        ProjCoordinate src = new ProjCoordinate(osm.lon(), osm.lat());
        ProjCoordinate dst = new ProjCoordinate();
        if (transform.transform(src, dst) == null) {
            throw new IllegalStateException(
                "Failed to transform coordinate (" + osm.lon() + ", " + osm.lat()
                    + ") of facility " + osm.id() + " into " + config.targetCrs());
        }

        ActivityFacility facility = new ActivityFacility(
            Id.create(osm.id(), ActivityFacility.class),
            new Coord(dst.x, dst.y));

        if (osm.isHousehold()) {
            ActivityOption home = new ActivityOption("home");
            home.setCapacity(estimateHouseholdCapacity(
                osm.areaM2(), osm.levels(), config.personsPerSqm()));
            facility.addActivityOption(home);
        } else {
            String activityType = config.activityFor(osm.osmKey(), osm.osmValue());
            facility.addActivityOption(new ActivityOption(activityType));
        }

        if (osm.name() != null) {
            facility.setDesc(osm.name());
        }
        if (osm.openingHours() != null) {
            facility.getAttributes().putAttribute("opening_hours", osm.openingHours());
        }
        for (Map.Entry<String, String> e : osm.address().entrySet()) {
            facility.getAttributes().putAttribute(e.getKey(), e.getValue());
        }
        return facility;
    }

    /**
     * Estimates the maximum number of persons a household can hold from its
     * footprint area and number of levels.
     */
    public static double estimateHouseholdCapacity(double areaM2, int levels, double personsPerSqm) {
        double effectiveArea = areaM2 * Math.max(1, levels);
        return Math.floor(effectiveArea * personsPerSqm);
    }
}
