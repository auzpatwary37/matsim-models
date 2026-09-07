package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OsmFacilitiesMainTest {
    @TempDir
    Path tempDir;

    @Test
    void mainGeneratesFacilitiesFile() throws Exception {
        Path osm = Path.of(getClass().getClassLoader()
            .getResource("fixtures/osm-facilities.osm").toURI());
        Path out = tempDir.resolve("facilities.xml");
        OsmFacilitiesMain.main(new String[]{osm.toString(), out.toString(), "EPSG:32617"});
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
    }
}
