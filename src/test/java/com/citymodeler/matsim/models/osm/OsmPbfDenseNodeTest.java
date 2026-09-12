package com.citymodeler.matsim.models.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OsmPbfDenseNodeTest {

    @Test
    void denseNodesAreReadWithDeltaEncodedIds() {
        OsmImportResult r = new OsmNetworkImporter().read(
                OsmImportConfig.of(Path.of("src/test/resources/osm/dense-ids.pbf"), "EPSG:3857"));

        assertEquals(3, r.nodes().size(), "all dense nodes must be read");
        assertTrue(r.nodes().containsKey("1000"));
        assertTrue(r.nodes().containsKey("1010"));
        assertTrue(r.nodes().containsKey("1022"));
        assertEquals("Alpha", r.nodes().get("1000").tags().get("name"));
        assertEquals("Beta", r.nodes().get("1010").tags().get("name"));

        // every way reference must resolve against the decoded node ids
        for (var way : r.ways().values()) {
            for (String ref : way.nodeRefs()) {
                assertTrue(r.nodes().containsKey(ref), "unresolved node ref " + ref);
            }
        }
    }
}
