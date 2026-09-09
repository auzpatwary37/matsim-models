package com.citymodeler.matsim.models.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.osm.model.OsmRelationMemberRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;

final class OsmNetworkImporterTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesMinimalNetwork() throws Exception {
        OsmImportConfig config = OsmImportConfig.of(Path.of("src/test/resources/osm/minimal-network.osm"), "EPSG:3857");
        OsmImportResult result = new OsmNetworkImporter().read(config);

        assertEquals(3, result.nodes().size());
        assertEquals(1, result.ways().size());
        assertEquals(List.of("1", "2", "3"), result.ways().get("10").nodeRefs());
        assertEquals("primary", result.ways().get("10").tags().get("highway"));
        assertEquals("Main Street", result.ways().get("10").tags().get("name"));
        assertEquals("50", result.ways().get("10").tags().get("maxspeed"));
        assertNotEquals(0.0, result.nodes().get("2").projectedCoord().getX());
        assertNotNull(result.provenance());
        assertEquals("ODbL-1.0", result.provenance().sourceLicense());
    }

    @Test
    void parsesStopsAndRelations() throws Exception {
        OsmImportConfig config = OsmImportConfig.of(Path.of("src/test/resources/osm/stops-and-relations.osm"), "EPSG:3857");
        OsmImportResult result = new OsmNetworkImporter().read(config);

        assertEquals(3, result.nodes().size());
        assertEquals(1, result.ways().size());
        assertEquals(1, result.relations().size());

        OsmRelationRecord relation = result.relations().get("300");
        assertNotNull(relation);
        assertEquals(3, relation.members().size());
        assertEquals(OsmElementType.WAY, relation.members().get(0).type());
        assertEquals("200", relation.members().get(0).ref());
        assertEquals("platform", relation.members().get(1).role());
        assertEquals("stop", relation.members().get(2).role());
        assertEquals("route", relation.tags().get("type"));
        assertEquals("bus", relation.tags().get("route"));
        assertEquals("B1", relation.tags().get("ref"));

        assertEquals("bus_stop", result.nodes().get("100").tags().get("highway"));
        assertEquals("Central", result.nodes().get("100").tags().get("name"));
        assertEquals("designated", result.ways().get("200").tags().get("bus"));
    }

    @Test
    void gzippedInputProducesSameResult() throws Exception {
        byte[] raw = Files.readAllBytes(Path.of("src/test/resources/osm/minimal-network.osm"));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(raw);
        }
        Path gzPath = tempDir.resolve("minimal-network.osm.gz");
        Files.write(gzPath, bos.toByteArray());

        OsmImportResult plain = new OsmNetworkImporter().read(OsmImportConfig.of(Path.of("src/test/resources/osm/minimal-network.osm"), "EPSG:3857"));
        OsmImportResult gzipped = new OsmNetworkImporter().read(OsmImportConfig.of(gzPath, "EPSG:3857"));

        assertEquals(plain.nodes().size(), gzipped.nodes().size());
        assertEquals(plain.ways().size(), gzipped.ways().size());
        assertEquals(plain.nodes().get("2").projectedCoord(), gzipped.nodes().get("2").projectedCoord());
    }

    @Test
    void keepRawTagsFalseStillParsesAllTagsIntoRecords() throws Exception {
        OsmImportConfig config = new OsmImportConfig(
                Path.of("src/test/resources/osm/minimal-network.osm"),
                "EPSG:3857",
                false,
                true,
                "OpenStreetMap",
                "ODbL-1.0",
                "\u00A9 OpenStreetMap contributors");
        OsmImportResult result = new OsmNetworkImporter().read(config);

        // Tags are always parsed into records regardless of keepRawTags;
        // the flag only controls provenance and link-attribute output.
        assertEquals(1, result.ways().size());
        assertEquals("primary", result.ways().get("10").tags().get("highway"));
        assertFalse(result.provenance().rawTagsKept());
    }
}
