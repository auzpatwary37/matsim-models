package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportConfig;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;
import com.citymodeler.matsim.models.osm.OsmNetworkImporter;

final class OsmNetworkEdgeCasesTest {

    private OsmNetworkBuildResult build(String osmContent) throws Exception {
        Path tmp = Path.of("target/test-osm-tmp.osm");
        java.nio.file.Files.writeString(tmp, osmContent);
        OsmImportResult result = new OsmNetworkImporter().read(OsmImportConfig.of(tmp, "EPSG:3857"));
        return new OsmMatsimNetworkBuilder().build(result, OsmNetworkBuildConfig.materializeGeometryConfig());
    }

    @Test
    void railwayRailProducesRailModeNotCar() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/><tag k="railway" v="rail"/></way>
                </osm>""");
        Link link = r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        assertNotNull(link);
        assertTrue(link.getAllowedModes().contains("rail"));
        assertTrue(link.getAllowedModes().contains("pt"));
        assertFalse(link.getAllowedModes().contains("car"));
    }

    @Test
    void tramProducesTramMode() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/><tag k="railway" v="tram"/></way>
                </osm>""");
        Link link = r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        assertTrue(link.getAllowedModes().contains("tram"));
        assertTrue(link.getAllowedModes().contains("pt"));
        assertFalse(link.getAllowedModes().contains("car"));
    }

    @Test
    void buswayProducesBusAndPtMode() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/><tag k="highway" v="busway"/></way>
                </osm>""");
        Link link = r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        assertTrue(link.getAllowedModes().contains("bus"));
        assertTrue(link.getAllowedModes().contains("pt"));
        assertFalse(link.getAllowedModes().contains("car"));
    }

    @Test
    void keepRawTagsFalseStillBuildsNetwork() throws Exception {
        OsmImportConfig config = new OsmImportConfig(
                Path.of("src/test/resources/osm/minimal-network.osm"),
                "EPSG:3857", false, true, "OpenStreetMap", "ODbL-1.0", "© OpenStreetMap contributors");
        OsmImportResult result = new OsmNetworkImporter().read(config);
        OsmNetworkBuildResult r = new OsmMatsimNetworkBuilder().build(result, OsmNetworkBuildConfig.materializeGeometryConfig());

        assertEquals(3, r.cleanedNetwork().getNodes().size());
        assertEquals(4, r.cleanedNetwork().getLinks().size());
    }

    @Test
    void lanesBothWaysNotDoubleCounted() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="primary"/>
                  <tag k="lanes" v="3"/>
                  <tag k="lanes:forward" v="1"/>
                  <tag k="lanes:backward" v="1"/>
                  <tag k="lanes:both_ways" v="1"/>
                </way></osm>""");
        Link forward = r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        assertEquals(1.0, forward.getNumberOfLanes(), 0.01);
    }

    @Test
    void onewayMinusOneProducesReverseOnlyLink() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="residential"/><tag k="oneway" v="-1"/>
                </way></osm>""");
        assertFalse(r.cleanedNetwork().getLinks().containsKey(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class)));
        assertNotNull(r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_r", Link.class)));
    }

    @Test
    void motorwayDefaultOnewayForward() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/><tag k="highway" v="motorway"/></way>
                </osm>""");
        assertNotNull(r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class)));
        assertFalse(r.cleanedNetwork().getLinks().containsKey(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_r", Link.class)));
    }

    @Test
    void onewayNoOnMotorwayProducesBidirectional() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="motorway"/><tag k="oneway" v="no"/>
                </way></osm>""");
        assertNotNull(r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class)));
        assertNotNull(r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_r", Link.class)));
    }

    @Test
    void onewayAlternatingEmitsWarningAndBuildsBidirectional() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="residential"/><tag k="oneway" v="alternating"/>
                </way></osm>""");
        assertTrue(r.cleanedNetwork().getLinks().containsKey(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class)));
        assertTrue(r.cleanedNetwork().getLinks().containsKey(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_r", Link.class)));
        assertTrue(r.issues().stream().anyMatch(i -> "dynamic-oneway".equals(i.code())));
    }

    @Test
    void accessPrivateRemovesCarMode() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="residential"/><tag k="access" v="private"/>
                </way></osm>""");
        // access=private is LEGALLY_RESTRICTED but not FORBIDDEN;
        // in our policy, restricted roads still produce links (conservative for routing).
        // The link should still exist.
        assertNotNull(r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class)));
    }

    @Test
    void accessNoWithBusDesignatedProducesBusOnly() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="busway"/><tag k="access" v="no"/><tag k="bus" v="designated"/>
                </way></osm>""");
        Link link = r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        assertNotNull(link);
        assertTrue(link.getAllowedModes().contains("bus"));
        // "pt" is not explicitly allowed (no pt=designated tag), so it doesn't survive
        assertFalse(link.getAllowedModes().contains("car"));
    }

    @Test
    void motorVehicleForwardNoRemovesCarFromForwardOnly() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="residential"/><tag k="motor_vehicle:forward" v="no"/>
                </way></osm>""");
        // Residential rule has only {car}; motor_vehicle:forward=no removes it forward
        // → no forward link, reverse link still has car
        assertFalse(r.cleanedNetwork().getLinks().containsKey(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class)));
        Link reverse = r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_r", Link.class));
        assertNotNull(reverse);
        assertTrue(reverse.getAllowedModes().contains("car"));
    }

    @Test
    void maxspeedNoneUsesRuleDefault() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="primary"/><tag k="maxspeed" v="none"/>
                </way></osm>""");
        Link link = r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        // primary rule freespeed = 38.89 m/s (140 km/h)
        assertEquals(38.89, link.getFreespeed(), 0.01);
    }

    @Test
    void maxspeedMphConvertsCorrectly() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="primary"/><tag k="maxspeed" v="55 mph"/>
                </way></osm>""");
        Link link = r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        double expected = 55.0 * 1.609344 / 3.6;
        assertEquals(expected, link.getFreespeed(), 0.01);
    }

    @Test
    void maxspeedMalformedFallsBackToRuleDefault() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="primary"/><tag k="maxspeed" v="fast"/>
                </way></osm>""");
        Link link = r.cleanedNetwork().getLinks().get(com.citymodeler.matsim.models.api.Id.create("osm_way_10_0_f", Link.class));
        assertEquals(38.89, link.getFreespeed(), 0.01);
    }

    @Test
    void missingNodeRefProducesIssueAndSkipsSegment() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <way id="10"><nd ref="1"/><nd ref="999"/>
                  <tag k="highway" v="residential"/>
                </way></osm>""");
        assertEquals(0, r.cleanedNetwork().getLinks().size());
        assertTrue(r.issues().stream().anyMatch(i -> i.severity() == OsmIssueSeverity.WARNING));
    }

    @Test
    void unrelatedNodesNotMaterialized() throws Exception {
        OsmNetworkBuildResult r = build("""
                <?xml version="1.0"?><osm version="0.6">
                <node id="1" lat="0.0" lon="0.0"/>
                <node id="2" lat="0.0" lon="0.001"/>
                <node id="99" lat="5.0" lon="5.0"><tag k="building" v="yes"/></node>
                <way id="10"><nd ref="1"/><nd ref="2"/>
                  <tag k="highway" v="residential"/>
                </way></osm>""");
        assertEquals(2, r.cleanedNetwork().getNodes().size());
    }
}
