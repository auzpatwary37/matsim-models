package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportConfig;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmNetworkImporter;

final class OsmMatsimNetworkBuilderTest {

    private OsmMatsimNetworkBuilder builder;
    private OsmNetworkBuildConfig config;

    @BeforeEach
    void setUp() {
        builder = new OsmMatsimNetworkBuilder();
        config = OsmNetworkBuildConfig.materializeGeometryConfig();
    }

    @Test
    void buildsBidirectionalLinksFromMinimalNetwork() throws Exception {
        OsmImportResult result = new OsmNetworkImporter().read(
                OsmImportConfig.of(Path.of("src/test/resources/osm/minimal-network.osm"), "EPSG:3857"));
        OsmNetworkBuildResult buildResult = builder.build(result, config);

        Network network = buildResult.cleanedNetwork();
        assertEquals(3, network.getNodes().size());
        assertEquals(4, network.getLinks().size());

        long forwardCount = buildResult.linkIdsByOsmWayId().get("10").stream()
                .filter(id -> id.endsWith("_f")).count();
        long reverseCount = buildResult.linkIdsByOsmWayId().get("10").stream()
                .filter(id -> id.endsWith("_r")).count();
        assertEquals(2, forwardCount);
        assertEquals(2, reverseCount);

        Link link = network.getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        assertNotNull(link);
        assertTrue(link.getLength() > 0.0);
        assertEquals("10", link.getAttributes().getAttribute("osm:wayId"));
        assertEquals("primary", link.getAttributes().getAttribute("osm:value"));
        assertTrue(link.getAllowedModes().contains("car"));
    }

    @Test
    void buildsDirectedLinksWithAccessAndSpeed() throws Exception {
        OsmImportResult result = new OsmNetworkImporter().read(
                OsmImportConfig.of(Path.of("src/test/resources/osm/access-oneway-lanes.osm"), "EPSG:3857"));
        OsmNetworkBuildResult buildResult = builder.build(result, config);

        Network network = buildResult.cleanedNetwork();
        assertEquals(3, network.getNodes().size());
        assertEquals(3, network.getLinks().size());

        assertEquals(2, buildResult.linkIdsByOsmWayId().get("10").size());
        assertEquals(1, buildResult.linkIdsByOsmWayId().get("11").size());

        Link carLink = network.getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        assertNotNull(carLink);
        double expectedSpeed = 35.0 * 1.609344 / 3.6;
        assertEquals(expectedSpeed, carLink.getFreespeed(), 0.01);
        assertEquals(2.0, carLink.getNumberOfLanes(), 0.01);

        Link busLink = network.getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_11_0_f", Link.class));
        assertNotNull(busLink);
        assertTrue(busLink.getAllowedModes().contains("bus"));
        assertTrue(busLink.getAllowedModes().contains("pt"));
    }
}
