package com.citymodeler.matsim.models.facilities.osm;

import java.nio.file.Path;
import java.util.List;

import com.citymodeler.matsim.models.facilities.ActivityFacilities;

/**
 * Command-line entry point to generate a MATSim facilities file from an OSM
 * extract.
 *
 * <p>Usage: {@code OsmFacilitiesMain <osm-file> <output-facilities.xml> <target-crs>}</p>
 */
public final class OsmFacilitiesMain {

    private OsmFacilitiesMain() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println(
                "Usage: OsmFacilitiesMain <osm-file> <output-facilities.xml> <target-crs>");
            throw new IllegalArgumentException(
                "Usage: OsmFacilitiesMain <osm-file> <output-facilities.xml> <target-crs>");
        }
        Path osmFile = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String targetCrs = args[2];

        OsmFacilityConfig config = OsmFacilityConfig.defaults(targetCrs);
        // Constructed before parse so an invalid/blank CRS fails fast, before the
        // expensive two-pass OSM read.
        OsmFacilityConverter converter = new OsmFacilityConverter(config);
        List<OsmFacility> parsed = new OsmFacilityParser(config).parse(osmFile);
        ActivityFacilities facilities = converter.convert(parsed);
        new OsmFacilityWriter().write(facilities, output);

        System.err.println("Wrote " + facilities.getFacilities().size()
            + " facilities to " + output);
    }
}
