package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.osm.OsmImportConfig;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmNetworkImporter;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

class LaneDefinitionBuilderTest {

    private final LaneDefinitionBuilder builder = new LaneDefinitionBuilder();
    private final OsmNetworkBuildConfig config = OsmNetworkBuildConfig.materializeGeometryConfig();

    /**
     * Junction: incoming l_in (a→b) with outgoing l_t (through), l_l (left) and l_r (right).
     */
    private Network junction() {
        Network network = new Network();
        network.createNode("a", 0, 0);
        network.createNode("b", 100, 0);
        network.createNode("c", 200, 0);
        network.createNode("d", 100, 100);
        network.createNode("e", 100, -100);
        network.createLink("l_in", "a", "b", 100, 900, 10, 1, Set.of("car"));
        network.createLink("l_t", "b", "c", 100, 900, 10, 1, Set.of("car"));
        network.createLink("l_l", "b", "d", 100, 900, 10, 1, Set.of("car"));
        network.createLink("l_r", "b", "e", 100, 900, 10, 1, Set.of("car"));
        return network;
    }

    private LaneDefinitionResult build(Network network, OsmWayRecord way) {
        Map<String, OsmLinkRef> refs = Map.of("l_in", new OsmLinkRef("l_in", way.id(), 0, true));
        Map<String, OsmWayRecord> ways = Map.of(way.id(), way);
        return builder.build(network, refs, ways, OsmGeometryStore.empty(), config);
    }

    private static OsmWayRecord way(Map<String, String> tags) {
        return new OsmWayRecord("7", List.of("a", "b"), OsmTagSet.of(tags));
    }

    private static Lane lane(LaneDefinitionResult result) {
        LanesToLinkAssignment assignment =
                result.lanes().getLanesToLinkAssignments().get(Id.create("l_in", Link.class));
        return assignment.getLanes().values().iterator().next();
    }

    private static int laneCount(LaneDefinitionResult result) {
        return result.lanes().getLanesToLinkAssignments().get(Id.create("l_in", Link.class))
                .getLanes().size();
    }

    @Test
    void ruleDefaultOnewayWayIsTreatedOneWayForLanes() {
        // Spec §1a direction source: a highway=motorway way has no literal oneway tag but the rule
        // defaults to one-way, so the network emits one link and lanes=* is that direction's count.
        // The builder must agree, taking lanes directly instead of halving a bidirectional total.
        Network network = new Network();
        network.createNode("a", 0, 0);
        network.createNode("b", 1, 0);
        network.createNode("c", 2, 0);
        network.createLink("l_in", "a", "b", 100, 1200, 47.2, 3, Set.of("car"));
        network.createLink("l_out", "b", "c", 100, 1200, 47.2, 3, Set.of("car"));

        LaneDefinitionResult result = build(network, way(Map.of(
                "highway", "motorway", "lanes", "3")));

        assertEquals(1, result.lanes().getLanesToLinkAssignments().size());
        assertEquals(3, laneCount(result), "motorway is one-way by rule: lanes=3 is not halved");
        assertEquals(3, lane(result).getAttributes().getAttribute("osm:lanes.count"),
                "the one-way count is the literal lanes total, not a halved bidirectional total");
    }

    @Test
    void motorwayLaneDirectionMatchesEmittedNetworkLink() throws Exception {
        // End-to-end: the built network emits a single directed link for a rule-default one-way
        // motorway, and the lane builder must treat that (only) link as one-way, so lanes=3 is the
        // travelled direction's count rather than a halved bidirectional total.
        OsmImportResult importResult = new OsmNetworkImporter().read(OsmImportConfig.of(
                Path.of("src/test/resources/osm/motorway-no-oneway-tag.osm"), "EPSG:3857"));
        OsmNetworkBuildResult built = new OsmMatsimNetworkBuilder().build(importResult, config);
        Network network = built.cleanedNetwork();

        var motorwayLinks = built.linkIdsByOsmWayId().get("20");
        assertNotNull(motorwayLinks);
        assertTrue(motorwayLinks.stream().allMatch(id -> id.endsWith("_f")),
                "rule-default one-way motorway emits only forward directed links: " + motorwayLinks);

        LaneDefinitionResult result = builder.build(network, built.linkRefsByLinkId(),
                importResult.ways(), built.geometryStore(), config);

        for (String linkId : motorwayLinks) {
            LanesToLinkAssignment assignment = result.lanes()
                    .getLanesToLinkAssignments().get(Id.create(linkId, Link.class));
            assertNotNull(assignment, "the emitted motorway link must have a lane assignment");
            assertEquals(3, assignment.getLanes().size(),
                    "lanes=3 on a one-way motorway is 3 lanes, matching network permlanes");
            assertEquals(network.getLinks().get(Id.create(linkId, Link.class)).getNumberOfLanes(),
                    assignment.getLanes().size(), 1e-9,
                    "laneDefinitions must agree with network.xml permlanes");
        }
    }

    @Test
    void untaggedLinkUsesRuleDefaultLaneCount() {
        // Spec §1a step 5: no lane tags -> the network's own rule.lanesPerDirection() (2 for primary).
        Network network = junction();
        LaneDefinitionResult result = build(network, way(Map.of("highway", "primary")));

        assertEquals(2, laneCount(result), "untagged primary must match network permlanes (2)");
        assertEquals("absent", lane(result).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void bothWaysIsCarriedAsProvenanceOnTheLaneAndNotDuplicated() {
        // Spec §1a step 4: lanes=5, lanes:both_ways=1 -> 2 directed lanes, and each lane records
        // the both_ways count as provenance; the centre lane is not added to either direction.
        Network network = junction();
        LaneDefinitionResult result = build(network, way(Map.of(
                "highway", "primary", "lanes", "5", "lanes:both_ways", "1")));

        assertEquals(2, laneCount(result), "per-direction count excludes the both_ways lane");
        assertEquals(1.0, lane(result).getAttributes().getAttribute("osm:lanes.bothWays"));
    }

    @Test
    void bareTurnLanesOnEvenSplitBidirectionalWayIsApplied() {
        Network network = junction();
        LaneDefinitionResult result = build(network, way(Map.of(
                "highway", "primary", "lanes", "2", "turn:lanes", "through")));

        assertEquals(1, result.lanes().getLanesToLinkAssignments().size());
        assertEquals(List.of("l_t"),
                lane(result).getToLinkIds().stream().map(Object::toString).toList());
        assertFalse(result.issues().stream().anyMatch(i -> "ambiguous-turn-lanes".equals(i.code())));
    }

    @Test
    void bareTurnLanesOnBidirectionalUndeterminedSplitIsNotApplied() {
        Network network = junction();
        LaneDefinitionResult result = build(network, way(Map.of(
                "highway", "primary", "lanes", "3", "turn:lanes", "left|through")));

        assertTrue(result.issues().stream().anyMatch(i -> "ambiguous-turn-lanes".equals(i.code())),
                "odd split makes a bare turn:lanes ambiguous: " + result.issues());
        // Not applied: the single undetermined lane falls back to all outgoing movements.
        assertEquals(3, lane(result).getToLinkIds().size());
        assertEquals(LaneConfidence.ABSENT,
                lane(result).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void bareTurnLanesOnBidirectionalWayWithoutLaneCountIsNotApplied() {
        Network network = junction();
        LaneDefinitionResult result = build(network, way(Map.of(
                "highway", "primary", "turn:lanes", "left|through")));

        assertEquals(1, result.lanes().getLanesToLinkAssignments().size());
        assertEquals(3, lane(result).getToLinkIds().size(), "no count -> no turn:lanes alignment");
    }

    @Test
    void bareTurnLanesOnOnewayWayIsApplied() {
        Network network = junction();
        LaneDefinitionResult result = build(network, way(Map.of(
                "highway", "primary", "oneway", "yes", "lanes", "2",
                "turn:lanes", "left|through")));

        LanesToLinkAssignment assignment =
                result.lanes().getLanesToLinkAssignments().get(Id.create("l_in", Link.class));
        assertEquals(2, assignment.getLanes().size());
        assertEquals(List.of("l_l"), assignment.getLanes().get(Id.create("l_in_l0", Lane.class))
                .getToLinkIds().stream().map(Object::toString).toList());
        assertEquals(List.of("l_t"), assignment.getLanes().get(Id.create("l_in_l1", Lane.class))
                .getToLinkIds().stream().map(Object::toString).toList());
    }

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
