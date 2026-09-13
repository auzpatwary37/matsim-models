package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

class LaneDefinitionBuilderTest {

    private final LaneDefinitionBuilder builder = new LaneDefinitionBuilder();
    private final OsmNetworkBuildConfig config = OsmNetworkBuildConfig.materializeGeometryConfig();

    @Test
    void missingWayIsReportedNotSilentlyDropped() {
        Network network = new Network();
        network.createNode("a", 0, 0);
        network.createNode("b", 1, 0);
        network.createNode("c", 2, 0);
        network.createLink("l1", "a", "b", 100, 900, 10, 1, java.util.Set.of("car"));
        network.createLink("l2", "b", "c", 100, 900, 10, 1, java.util.Set.of("car"));

        Map<String, OsmLinkRef> refs = Map.of(
                "l1", new OsmLinkRef("l1", "999", 0, true));

        LaneDefinitionResult result = builder.build(network, refs, Map.of(), OsmGeometryStore.empty(),
                config);

        assertTrue(result.lanes().getLanesToLinkAssignments().isEmpty());
        assertTrue(result.issues().stream().anyMatch(i -> "lane-unresolved-way".equals(i.code())),
                "missing source way must produce a lane-unresolved-way issue: " + result.issues());
    }

    @Test
    void unresolvedRuleIsReportedNotSilentlyDropped() {
        Network network = new Network();
        network.createNode("a", 0, 0);
        network.createNode("b", 1, 0);
        network.createNode("c", 2, 0);
        network.createLink("l1", "a", "b", 100, 900, 10, 1, java.util.Set.of("car"));
        network.createLink("l2", "b", "c", 100, 900, 10, 1, java.util.Set.of("car"));

        Map<String, OsmLinkRef> refs = Map.of(
                "l1", new OsmLinkRef("l1", "7", 0, true));
        Map<String, OsmWayRecord> ways = Map.of(
                "7", new OsmWayRecord("7", List.of("a", "b"),
                        OsmTagSet.of(Map.of("highway", "footway"))));

        LaneDefinitionResult result = builder.build(network, refs, ways, OsmGeometryStore.empty(),
                config);

        assertTrue(result.lanes().getLanesToLinkAssignments().isEmpty());
        assertTrue(result.issues().stream().anyMatch(i -> "lane-unresolved-way".equals(i.code())),
                "unmatched rule must produce a lane-unresolved-way issue: " + result.issues());
        for (OsmImportIssue issue : result.issues()) {
            assertEquals("lane-unresolved-way", issue.code());
        }
    }
}
