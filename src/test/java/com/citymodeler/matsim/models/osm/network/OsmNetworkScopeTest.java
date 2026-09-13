package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/** Way-class scope: the default admits service; the compact-road preset excludes it. */
final class OsmNetworkScopeTest {

    private static OsmNodeRecord n(String id, double x) {
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.empty());
    }

    private static OsmImportResult withServiceWay() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0));
        ns.put("B", n("B", 100));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("highway", "service"))));
        return new OsmImportResult(ns, ws, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));
    }

    @Test
    void defaultConfigAdmitsService() {
        assertNotNull(OsmNetworkBuildConfig.defaultConfig()
                .resolveRule(OsmTagSet.of(Map.of("highway", "service"))));
        assertEquals(Set.of(), OsmNetworkBuildConfig.defaultConfig().excludedHighwayClasses());
    }

    @Test
    void compactRoadPresetExcludesService() {
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.compactRoadNetworkConfig();
        assertNull(cfg.resolveRule(OsmTagSet.of(Map.of("highway", "service"))));
        assertNotNull(cfg.resolveRule(OsmTagSet.of(Map.of("highway", "residential"))));
        assertTrue(cfg.excludedHighwayClasses().contains("service"));
    }

    @Test
    void compactRoadPresetDropsServiceWayFromNetwork() {
        OsmImportResult result = withServiceWay();

        CollapsedTopology withService = OsmTopologyBuilder.build(result,
                OsmNetworkBuildConfig.defaultConfig(), false);
        assertFalse(withService.network().getLinks().isEmpty(), "default keeps the service way");

        CollapsedTopology withoutService = OsmTopologyBuilder.build(result,
                OsmNetworkBuildConfig.compactRoadNetworkConfig(), false);
        assertTrue(withoutService.network().getLinks().isEmpty(),
                "compact preset drops the service way");
        assertEquals(0, withoutService.network().getNodes().size());
    }

    /** Bus is added to car roads so buses can be routed over the road network. */
    @Test
    void busIsAddedToCarRoads() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0));
        ns.put("B", n("B", 100));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("highway", "residential"))));
        OsmImportResult result = new OsmImportResult(ns, ws, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));

        var net = OsmTopologyBuilder.build(result, OsmNetworkBuildConfig.defaultConfig(), false).network();
        var modes = net.getLinks().values().iterator().next().getAllowedModes();
        assertTrue(modes.contains("car"));
        assertTrue(modes.contains("bus"), "car roads must also allow bus, got " + modes);
    }

    /** The contracted-link length cap must not split rail links (it applies to car roads only). */
    @Test
    void lengthCapDoesNotSplitRail() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0));
        ns.put("B", n("B", 400));
        ns.put("C", n("C", 800));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B", "C"),
                OsmTagSet.of(Map.of("railway", "rail"))));
        OsmImportResult result = new OsmImportResult(ns, ws, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));

        var net = OsmTopologyBuilder.build(result,
                OsmNetworkBuildConfig.compactRoadNetworkConfig(), false).network();
        // Rail is bidirectional and not split by the car length cap: A and C retained only, so one
        // physical span -> 2 directed links.
        assertEquals(2, net.getLinks().size(),
                "rail must not be split by the car length cap");
    }

    /** Rail and tram tracks are bidirectional by default; only an explicit oneway tag makes oneway. */
    @Test
    void railAndTramAreBidirectionalByDefault() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0));
        ns.put("B", n("B", 100));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("railway", "tram"))));
        ws.put("20", new OsmWayRecord("20", List.of("A", "B"),
                OsmTagSet.of(Map.of("railway", "rail"))));
        OsmImportResult result = new OsmImportResult(ns, ws, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));

        var net = OsmTopologyBuilder.build(result,
                OsmNetworkBuildConfig.compactRoadNetworkConfig(), false).network();
        // tram: 2 directed; rail: 2 directed => 4 total.
        assertEquals(4, net.getLinks().size(),
                "rail and tram are bidirectional unless explicitly tagged oneway");
    }

    /** An explicit oneway tag makes a car road a single directed link (raw engine, no cleaning). */
    @Test
    void explicitOnewayTagYieldsSingleDirectedLink() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0));
        ns.put("B", n("B", 100));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("highway", "residential", "oneway", "yes"))));
        OsmImportResult result = new OsmImportResult(ns, ws, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));

        // materializeGeometryConfig does not clean; direction is what we assert here.
        var net = OsmTopologyBuilder.build(result,
                OsmNetworkBuildConfig.materializeGeometryConfig(), false).network();
        assertEquals(1, net.getLinks().size(),
                "an explicit oneway car road yields exactly one directed link");
    }
}
