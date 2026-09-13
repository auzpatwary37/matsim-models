package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

final class OsmLaneHintExtractorTest {

    private static OsmNodeRecord node(String id, double x) {
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.empty());
    }

    private static OsmImportResult wayWithLanes() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        Map<String, OsmWayRecord> ways = new TreeMap<>();
        // Bidirectional way carrying lanes=2 with explicit per-direction lanes, so the unchanged
        // OsmLaneResolver reports 2 lanes per direction (a bare lanes=2 would be halved to 1).
        ways.put("10", new OsmWayRecord("10", List.of("A", "B"), OsmTagSet.of(Map.of(
                "highway", "residential", "lanes", "2",
                "lanes:forward", "2", "lanes:backward", "2",
                "turn:lanes", "through|left"))));
        return new OsmImportResult(nodes, ways, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));
    }

    @Test
    void contractedModeKeysHintsByActualNetworkLinks() {
        OsmImportResult r = wayWithLanes();
        OsmNetworkBuildResult built = new OsmMatsimNetworkBuilder()
                .build(r, OsmNetworkBuildConfig.defaultConfig());

        Map<String, OsmLaneHint> hints = built.laneHintsByLinkId();
        assertFalse(hints.isEmpty(), "lane hints must not be empty in contracted mode");

        // A single degree-2 span collapses to the canonical sim_10_f_A_B / sim_10_r_A_B pair.
        OsmLaneHint forward = hints.get("sim_10_f_A_B");
        OsmLaneHint reverse = hints.get("sim_10_r_A_B");
        assertNotNull(forward, "forward merged link must carry a lane hint");
        assertNotNull(reverse, "reverse merged link must carry a lane hint");

        // lanes:forward=2 / lanes:backward=2 -> the resolver reports 2 lanes per direction.
        assertEquals(2.0, forward.totalLanes(), 1e-9);
        assertEquals(2.0, reverse.totalLanes(), 1e-9);
        assertEquals("through|left", forward.turnLanes());
        assertTrue(forward.servedModes().contains("car"));

        // Every hint is keyed by a link that actually exists in the built network.
        for (String linkId : hints.keySet()) {
            assertTrue(built.cleanedNetwork().getLinks().containsKey(
                            com.citymodeler.matsim.models.api.Id.create(linkId,
                                    com.citymodeler.matsim.models.network.Link.class)),
                    "hint keyed by a non-existent link: " + linkId);
        }
    }

    @Test
    void materializeModeKeepsAtomicLinkKeys() {
        OsmImportResult r = wayWithLanes();
        OsmNetworkBuildResult built = new OsmMatsimNetworkBuilder()
                .build(r, OsmNetworkBuildConfig.materializeGeometryConfig());

        assertNotNull(built.laneHintsByLinkId().get("osm_way_10_0_f"));
        assertNotNull(built.laneHintsByLinkId().get("osm_way_10_0_r"));
    }
}
