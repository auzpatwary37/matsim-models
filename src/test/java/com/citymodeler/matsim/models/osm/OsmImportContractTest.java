package com.citymodeler.matsim.models.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

final class OsmImportContractTest {
    @Test
    void defaultConfigCarriesOsmAttribution() {
        OsmImportConfig config = OsmImportConfig.of(Path.of("whatever.osm"), "EPSG:3857");

        assertEquals("EPSG:3857", config.targetCrs());
        assertTrue(config.keepRawTags());
        assertTrue(config.writeProvenanceAttributes());
        assertEquals("OpenStreetMap", config.sourceName());
        assertEquals("ODbL-1.0", config.sourceLicense());
        assertEquals("\u00A9 OpenStreetMap contributors", config.attributionText());
    }

    @Test
    void importResultAppliesProvenanceToNetworkAttributes() {
        OsmProvenance provenance = new OsmProvenance(
                "OpenStreetMap",
                "ODbL-1.0",
                "\u00A9 OpenStreetMap contributors",
                "minimal-network.osm",
                "EPSG:3857",
                "PRESERVE_AS_LINK_GEOMETRY",
                true,
                Instant.parse("2026-09-08T00:00:00Z"));
        OsmImportResult result = new OsmImportResult(
                Map.of("1", new OsmNodeRecord("1", 1.0, 2.0, new Coord(10.0, 20.0), OsmTagSet.empty())),
                Map.of(),
                Map.of(),
                List.of(),
                provenance);

        Network network = new Network("osm-network");
        result.applyProvenanceTo(network);

        assertEquals("OpenStreetMap", network.getAttributes().getAttribute("osm:source"));
        assertEquals("ODbL-1.0", network.getAttributes().getAttribute("osm:license"));
        assertEquals("\u00A9 OpenStreetMap contributors", network.getAttributes().getAttribute("osm:attribution"));
        assertEquals("minimal-network.osm", network.getAttributes().getAttribute("osm:input"));
        assertEquals("EPSG:3857", network.getAttributes().getAttribute("osm:targetCrs"));
    }

    @Test
    void resultCollectionsAreImmutable() {
        OsmImportResult result = new OsmImportResult(Map.of(), Map.of(), Map.of(), List.of(), OsmProvenance.defaultFor("input.osm", "EPSG:3857"));

        assertThrows(UnsupportedOperationException.class, () -> result.nodes().put("x", null));
        assertThrows(UnsupportedOperationException.class, () -> result.ways().put("x", new OsmWayRecord("x", List.of(), OsmTagSet.empty())));
        assertThrows(UnsupportedOperationException.class, () -> result.relations().put("x", new OsmRelationRecord("x", List.of(), OsmTagSet.empty())));
    }
}
