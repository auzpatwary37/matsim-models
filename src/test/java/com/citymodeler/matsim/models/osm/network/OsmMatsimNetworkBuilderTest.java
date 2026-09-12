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

    @Test
    void preservesGeometryModeContractsTopology() {
        // 5 collinear nodes, one way; default (PRESERVE) must collapse to 2 nodes.
        var ns = new java.util.TreeMap<String, com.citymodeler.matsim.models.osm.model.OsmNodeRecord>();
        for (int i = 0; i < 5; i++) {
            ns.put("N" + i, new com.citymodeler.matsim.models.osm.model.OsmNodeRecord(
                    "N" + i, i, 0, new com.citymodeler.matsim.models.api.Coord(i, 0),
                    com.citymodeler.matsim.models.osm.OsmTagSet.empty()));
        }
        var ws = new java.util.TreeMap<String, com.citymodeler.matsim.models.osm.model.OsmWayRecord>();
        ws.put("10", new com.citymodeler.matsim.models.osm.model.OsmWayRecord("10",
                java.util.List.of("N0", "N1", "N2", "N3", "N4"),
                com.citymodeler.matsim.models.osm.OsmTagSet.of(java.util.Map.of("highway", "residential"))));
        var r = new com.citymodeler.matsim.models.osm.OsmImportResult(ns, ws,
                new java.util.TreeMap<>(), java.util.List.of(),
                com.citymodeler.matsim.models.osm.OsmProvenance.defaultFor("f.osm", "EPSG:3857"));

        var built = new com.citymodeler.matsim.models.osm.network.OsmMatsimNetworkBuilder()
                .build(r, com.citymodeler.matsim.models.osm.network.OsmNetworkBuildConfig.defaultConfig());

        assertEquals(2, built.cleanedNetwork().getNodes().size());
        assertEquals(2, built.cleanedNetwork().getLinks().size()); // fwd + rev
        // 5 collinear OSM nodes form 4 atomic segments; contraction folds all four into one link.
        assertEquals("4", built.cleanedNetwork().getLinks().values().iterator().next()
                .getAttributes().getAttribute("osm:segmentCount"));

        // linkRefsByLinkId is flattened from the collapsed links' source segments: 4 atomic
        // segments x 2 travel directions, all indexed by their atomic id.
        assertEquals(8, built.linkRefsByLinkId().size());
        assertTrue(built.linkRefsByLinkId().containsKey("osm_way_10_0_f"));
        assertTrue(built.linkRefsByLinkId().containsKey("osm_way_10_3_r"));

        // linkIdsByOsmWayId indexes the two emitted merged links under the sole source way.
        assertEquals(2, built.linkIdsByOsmWayId().get("10").size());

        // The geometry sidecar is populated for the contracted link in a non-materialize mode.
        assertTrue(built.geometryStore().geometryForLink("sim_10_f_N0_N4").isPresent());
    }
}
