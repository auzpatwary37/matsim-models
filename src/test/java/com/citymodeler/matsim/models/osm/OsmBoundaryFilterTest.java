package com.citymodeler.matsim.models.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.osm.network.OsmMatsimNetworkBuilder;
import com.citymodeler.matsim.models.osm.network.OsmNetworkBuildConfig;
import com.citymodeler.matsim.models.osm.network.OsmNetworkBuildResult;

final class OsmBoundaryFilterTest {

    // Square around the center of the grid:
    // Grid spans lon -73.5673 to -73.5648, lat 45.5017 to 45.5042
    // Inner square: lon -73.5668 to -73.5653, lat 45.5022 to 45.5037
    private static final OsmBoundary INNER_SQUARE = new OsmBoundary(java.util.List.of(
            new double[]{-73.5668, 45.5022},
            new double[]{-73.5653, 45.5022},
            new double[]{-73.5653, 45.5037},
            new double[]{-73.5668, 45.5037},
            new double[]{-73.5668, 45.5022}
    ));

    @Test
    void validPolygonPasses() {
        OsmBoundary b = INNER_SQUARE.validate();
        assertTrue(b.signedArea() > 0);
        assertFalse(b.hasSelfIntersection());
    }

    @Test
    void unclosedPolygonRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OsmBoundary(java.util.List.of(
                new double[]{0, 0},
                new double[]{1, 0},
                new double[]{1, 1},
                new double[]{0, 1}
        )));
    }

    @Test
    void clockwisePolygonHasNegativeArea() {
        OsmBoundary cw = new OsmBoundary(java.util.List.of(
                new double[]{0, 0},
                new double[]{0, 1},
                new double[]{1, 1},
                new double[]{1, 0},
                new double[]{0, 0}
        ));
        assertTrue(cw.signedArea() < 0);
        assertThrows(IllegalArgumentException.class, cw::validate);
    }

    @Test
    void selfIntersectingPolygonRejected() {
        // Bowtie shape
        OsmBoundary bowtie = new OsmBoundary(java.util.List.of(
                new double[]{0, 0},
                new double[]{1, 1},
                new double[]{1, 0},
                new double[]{0, 1},
                new double[]{0, 0}
        ));
        assertTrue(bowtie.hasSelfIntersection());
        assertThrows(IllegalArgumentException.class, bowtie::validate);
    }

    @Test
    void pointInPolygon() {
        assertTrue(INNER_SQUARE.contains(-73.5660, 45.5030));
        assertFalse(INNER_SQUARE.contains(-73.5700, 45.5030));
        assertFalse(INNER_SQUARE.contains(-73.5660, 45.4990));
    }

    @Test
    void boundaryFilterReducesGrid() throws Exception {
        Path fixture = Path.of("src/test/resources/osm/grid-integration.osm");

        // Without boundary: full grid
        OsmImportResult full = new OsmNetworkImporter().read(OsmImportConfig.of(fixture, "EPSG:3857"));
        assertEquals(66, full.ways().size());

        // With inner square boundary: subset
        OsmImportResult filtered = new OsmNetworkImporter().read(
                OsmImportConfig.of(fixture, "EPSG:3857", INNER_SQUARE));
        assertTrue(filtered.nodes().size() < full.nodes().size(),
                "Filtered nodes " + filtered.nodes().size() + " should be < " + full.nodes().size());
        assertTrue(filtered.ways().size() < full.ways().size(),
                "Filtered ways " + filtered.ways().size() + " should be < " + full.ways().size());
        assertTrue(filtered.ways().size() > 0, "Should still have some ways");
    }

    @Test
    void boundaryFilteredBuildProducesSmallerNetwork() throws Exception {
        Path fixture = Path.of("src/test/resources/osm/grid-integration.osm");

        OsmImportResult fullResult = new OsmNetworkImporter().read(OsmImportConfig.of(fixture, "EPSG:3857"));
        OsmNetworkBuildResult fullBuild = new OsmMatsimNetworkBuilder().build(
                fullResult, OsmNetworkBuildConfig.materializeGeometryConfig());

        OsmImportResult filteredResult = new OsmNetworkImporter().read(
                OsmImportConfig.of(fixture, "EPSG:3857", INNER_SQUARE));
        OsmNetworkBuildResult filteredBuild = new OsmMatsimNetworkBuilder().build(
                filteredResult, OsmNetworkBuildConfig.materializeGeometryConfig());

        assertTrue(filteredBuild.cleanedNetwork().getLinks().size() < fullBuild.cleanedNetwork().getLinks().size());
        assertTrue(filteredBuild.cleanedNetwork().getNodes().size() < fullBuild.cleanedNetwork().getNodes().size());
    }

    @Test
    void geofabrikRegionResolution() {
        // Montreal-area polygon → should resolve to MONTREAL or QUEBEC
        OsmBoundary montreal = new OsmBoundary(java.util.List.of(
                new double[]{-73.58, 45.49},
                new double[]{-73.55, 45.49},
                new double[]{-73.55, 45.52},
                new double[]{-73.58, 45.52},
                new double[]{-73.58, 45.49}
        ));
        var region = GeofabrikRegions.resolveSmallest(montreal);
        assertTrue(region.isPresent());
        assertTrue(region.get().name().contains("montreal") || region.get().name().contains("quebec"),
                "Expected montreal or quebec, got " + region.get().name());
    }

    @Test
    void geofabrikFallsBackToLargerRegion() {
        // Large polygon spanning multiple provinces → should get CANADA
        OsmBoundary large = new OsmBoundary(java.util.List.of(
                new double[]{-120, 50},
                new double[]{-60, 50},
                new double[]{-60, 60},
                new double[]{-120, 60},
                new double[]{-120, 50}
        ));
        var region = GeofabrikRegions.resolveSmallest(large);
        assertTrue(region.isPresent());
        assertTrue(region.get().name().equals("canada") || region.get().name().equals("north-america"),
                "Expected canada or north-america, got " + region.get().name());
    }
}
