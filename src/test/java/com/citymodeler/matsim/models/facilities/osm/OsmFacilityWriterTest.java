package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.facilities.ActivityOption;
import com.citymodeler.matsim.models.io.FacilitiesXmlReader;

class OsmFacilityWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesReadableFacilitiesXml() throws Exception {
        ActivityFacilities facilities = new ActivityFacilities("test");
        ActivityFacility f = new ActivityFacility(
            Id.create("1", ActivityFacility.class), new Coord(600000.0, 4800000.0));
        f.addActivityOption(new ActivityOption("home"));
        facilities.addFacility(f);

        Path out = tempDir.resolve("facilities.xml");
        new OsmFacilityWriter().write(facilities, out);

        assertTrue(Files.exists(out));
        ActivityFacilities read = new FacilitiesXmlReader().read(Files.newInputStream(out));
        assertEquals(1, read.getFacilities().size());
        assertEquals("home", read.getFacilities().values().iterator().next()
            .getActivityOptions().get("home").getType());
    }
}
