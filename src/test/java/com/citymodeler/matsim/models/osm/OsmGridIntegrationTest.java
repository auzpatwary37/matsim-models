package com.citymodeler.matsim.models.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.network.OsmMatsimNetworkBuilder;
import com.citymodeler.matsim.models.osm.network.OsmNetworkBuildConfig;
import com.citymodeler.matsim.models.osm.network.OsmNetworkBuildResult;

final class OsmGridIntegrationTest {

    private static final Path FIXTURE = Path.of("src/test/resources/osm/grid-integration.osm");

    private OsmNetworkBuildResult buildGrid() throws IOException {
        OsmImportConfig config = OsmImportConfig.of(FIXTURE, "EPSG:3857");
        OsmImportResult result = new OsmNetworkImporter().read(config);
        return new OsmMatsimNetworkBuilder().build(result, OsmNetworkBuildConfig.materializeGeometryConfig());
    }

    @Test
    void importsAllElements() throws IOException {
        OsmImportConfig config = OsmImportConfig.of(FIXTURE, "EPSG:3857");
        OsmImportResult result = new OsmNetworkImporter().read(config);
        assertEquals(41, result.nodes().size());
        assertEquals(66, result.ways().size());
        assertEquals(0, result.issues().size());
    }

    @Test
    void poiNodesExcludedFromNetwork() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        // 36 grid nodes, 5 POI nodes excluded
        assertEquals(36, r.cleanedNetwork().getNodes().size());
    }

    @Test
    void producesExpectedLinkCount() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        // 66 ways, most bidirectional → ~2 links per way, minus oneways and special cases
        assertTrue(r.cleanedNetwork().getLinks().size() >= 100,
                "Expected at least 100 links, got " + r.cleanedNetwork().getLinks().size());
    }

    @Test
    void railWaysProduceRailMode() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        Network net = r.cleanedNetwork();

        long railLinks = net.getLinks().values().stream()
                .filter(l -> l.getAllowedModes().contains("rail"))
                .count();
        assertTrue(railLinks >= 3, "Expected at least 3 rail links, got " + railLinks);

        Link railLink = net.getLinks().values().stream()
                .filter(l -> l.getAllowedModes().contains("rail"))
                .findFirst().orElseThrow();
        assertEquals(83.33, railLink.getFreespeed(), 0.1);
    }

    @Test
    void tramWaysProduceTramMode() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        Network net = r.cleanedNetwork();

        long tramLinks = net.getLinks().values().stream()
                .filter(l -> l.getAllowedModes().contains("tram"))
                .count();
        assertTrue(tramLinks >= 3, "Expected at least 3 tram links, got " + tramLinks);

        Link tramLink = net.getLinks().values().stream()
                .filter(l -> l.getAllowedModes().contains("tram"))
                .findFirst().orElseThrow();
        assertEquals(16.67, tramLink.getFreespeed(), 0.1);
    }

    @Test
    void buswayIsBusOnly() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        Network net = r.cleanedNetwork();

        var buswayLinks = net.getLinks().values().stream()
                .filter(l -> l.getAllowedModes().contains("bus") && !l.getAllowedModes().contains("car"))
                .toList();
        assertTrue(buswayLinks.size() >= 4, "Expected at least 4 busway links, got " + buswayLinks.size());

        for (Link link : buswayLinks) {
            assertFalse(link.getAllowedModes().contains("car"));
            assertTrue(link.getAllowedModes().contains("bus"));
        }
    }

    @Test
    void onewayProducesSingleDirectionLink() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        Network net = r.cleanedNetwork();

        // Way 107 has oneway=yes → forward only
        var fwd = com.citymodeler.matsim.models.api.Id.create("osm_way_107_0_f", Link.class);
        var rev = com.citymodeler.matsim.models.api.Id.create("osm_way_107_0_r", Link.class);
        assertTrue(net.getLinks().containsKey(fwd), "Forward link should exist");
        assertFalse(net.getLinks().containsKey(rev), "Reverse link should not exist for oneway=yes");
    }

    @Test
    void onewayReverseProducesReverseOnlyLink() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        Network net = r.cleanedNetwork();

        // Way 113 has oneway=-1 → reverse only
        var fwd = com.citymodeler.matsim.models.api.Id.create("osm_way_113_0_f", Link.class);
        var rev = com.citymodeler.matsim.models.api.Id.create("osm_way_113_0_r", Link.class);
        assertFalse(net.getLinks().containsKey(fwd), "Forward link should not exist for oneway=-1");
        assertTrue(net.getLinks().containsKey(rev), "Reverse link should exist");
    }

    @Test
    void maxspeedMphConvertsCorrectly() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        Network net = r.cleanedNetwork();

        // Way 104 (row 0, col 4) has maxspeed=50 mph → 22.35 m/s
        var id = com.citymodeler.matsim.models.api.Id.create("osm_way_104_0_f", Link.class);
        Link link = net.getLinks().get(id);
        assertNotNull(link);
        double expected = 50.0 * 1.609344 / 3.6;
        assertEquals(expected, link.getFreespeed(), 0.01);
    }

    @Test
    void maxspeedNoneUsesRuleDefault() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        Network net = r.cleanedNetwork();

        // Way 904 has maxspeed=none → falls back to primary rule (38.89 m/s)
        var id = com.citymodeler.matsim.models.api.Id.create("osm_way_904_0_f", Link.class);
        Link link = net.getLinks().get(id);
        assertNotNull(link);
        assertEquals(38.89, link.getFreespeed(), 0.01);
    }

    @Test
    void missingNodeRefGeneratesWarning() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        assertTrue(r.issues().stream().anyMatch(i -> "missing-node".equals(i.code())),
                "Expected missing-node warning for way 905");
    }

    @Test
    void modeDistributionIsReasonable() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        Network net = r.cleanedNetwork();

        Map<String, Long> modes = new TreeMap<>();
        net.getLinks().values().forEach(l -> l.getAllowedModes().forEach(m -> modes.merge(m, 1L, Long::sum)));

        assertTrue(modes.containsKey("car"), "Expected car mode");
        assertTrue(modes.get("car") > 50, "Expected many car links, got " + modes.get("car"));
        assertTrue(modes.containsKey("rail"), "Expected rail mode");
        assertTrue(modes.containsKey("tram"), "Expected tram mode");
        assertTrue(modes.containsKey("bus"), "Expected bus mode");
    }

    @Test
    void provenanceAttached() throws IOException {
        OsmNetworkBuildResult r = buildGrid();
        Network net = r.cleanedNetwork();
        assertNotNull(net.getAttributes());
    }
}
