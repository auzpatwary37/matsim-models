package com.citymodeler.matsim.models.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.io.LanesXmlReader;
import com.citymodeler.matsim.models.lanes.Lanes;

class OsmGtfsBundleRunnerLanesTest {

    @Test
    void bundleAlwaysWritesLaneDefinitionsWithOneLanePerLink(@TempDir Path out) throws Exception {
        Path osm = Path.of("src/test/resources/osm/access-oneway-lanes.osm");
        OsmGtfsBundleRunner.BundleResult result =
                new OsmGtfsBundleRunner().run(osm, null, null, out);

        assertNotNull(result.lanesFile());
        assertTrue(Files.exists(result.lanesFile()));
        assertTrue(result.laneAssignments() >= result.baseNetworkLinks(),
                "every emitted link must carry at least one lane");

        Lanes lanes = new LanesXmlReader().read(result.lanesFile());
        assertEquals(result.laneAssignments(), lanes.getLanesToLinkAssignments().size());
    }
}
