package com.citymodeler.matsim.models.facilities.osm;

import java.nio.file.Path;

import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.io.FacilitiesXmlWriter;

/**
 * Writes {@link ActivityFacilities} to a MATSim {@code facilities.xml} file,
 * delegating to the existing {@link FacilitiesXmlWriter}.
 */
public final class OsmFacilityWriter {

    private final FacilitiesXmlWriter delegate = new FacilitiesXmlWriter();

    public void write(ActivityFacilities facilities, Path path) {
        delegate.write(facilities, path);
    }
}
