package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * The base network ({@code network.xml} {@code permlanes}) and {@code laneDefinitions.xml} must derive
 * their lane counts from the SAME validated hierarchy (spec §1a), so the two artifacts never disagree.
 * These tests build a network from an OSM way and compare the emitted link's lane count against
 * {@link OsmDirectionalLaneResolver} for the same tags.
 */
final class OsmLaneCountAlignmentTest {

    private static OsmNodeRecord node(String id, double x) {
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.empty());
    }

    private static OsmImportResult way(Map<String, String> tags) {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", new OsmWayRecord("10", List.of("A", "B"), OsmTagSet.of(tags)));
        return new OsmImportResult(nodes, ways, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));
    }

    private static double forwardPermlanes(OsmImportResult r) {
        Network net = OsmTopologyBuilder.build(r, OsmNetworkBuildConfig.materializeGeometryConfig(), false)
                .network();
        Link link = net.getLinks().get(Id.createLinkId("sim_10_f_A_B"));
        assertNotNull(link, "expected the forward emitted link");
        return link.getNumberOfLanes();
    }

    private static OsmDirectionalLaneResolver resolver() {
        return new OsmDirectionalLaneResolver();
    }

    @Test
    void contradictoryDirectionalTagsDoNotInflateNetworkLanes() {
        // lanes=2, forward=3, backward=1 sums to 4 > 2: the old network resolver trusted 3; the shared
        // resolver flags it and falls back to the total split (2 -> 1 per direction).
        OsmImportResult r = way(Map.of("highway", "residential", "lanes", "2",
                "lanes:forward", "3", "lanes:backward", "1"));
        double networkLanes = forwardPermlanes(r);
        OsmLaneCount expected = resolver().resolve(
                r.ways().get("10").tags(), true, false, 1.0);
        assertTrue(expected.issueCodes().contains("inconsistent-lane-tags"));
        assertEquals(expected.lanes(), networkLanes, 1e-9);
        assertEquals(1.0, networkLanes, 1e-9, "contradictory forward=3 must not become the network count");
    }

    @Test
    void networkBuildSurfacesLaneTagDiagnostics() {
        // The base-network path must not silently drop the resolver's diagnostics: contradictory tags
        // surface `inconsistent-lane-tags`, odd totals surface `undetermined-lane-split`, matching
        // what laneDefinitions.xml reports.
        var inconsistent = OsmTopologyBuilder.build(
                way(Map.of("highway", "residential", "lanes", "2",
                        "lanes:forward", "3", "lanes:backward", "1")),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false);
        assertTrue(hasIssue(inconsistent.issues(), "inconsistent-lane-tags"),
                "network build must surface inconsistent-lane-tags");

        var oddTotal = OsmTopologyBuilder.build(
                way(Map.of("highway", "primary", "lanes", "3")),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false);
        assertTrue(hasIssue(oddTotal.issues(), "undetermined-lane-split"),
                "network build must surface undetermined-lane-split for an odd total");
    }

    private static boolean hasIssue(java.util.List<com.citymodeler.matsim.models.osm.OsmImportIssue> issues,
                                    String code) {
        return issues.stream().anyMatch(i -> code.equals(i.code()));
    }

    @Test
    void oddBidirectionalTotalIsIntegralInNetworkLanes() {
        // lanes=3 (odd): the network must not emit a fractional 1.5 permlanes; the shared resolver
        // rounds to 2 with an undetermined-split flag, matching laneDefinitions.
        OsmImportResult r = way(Map.of("highway", "primary", "lanes", "3"));
        double networkLanes = forwardPermlanes(r);
        OsmLaneCount expected = resolver().resolve(r.ways().get("10").tags(), true, false, 2.0);
        assertEquals(expected.lanes(), networkLanes, 1e-9);
        assertEquals(2.0, networkLanes, 1e-9);
        assertEquals(LaneConfidence.UNDETERMINED_SPLIT, expected.confidence());
        assertEquals(3.0, expected.undeterminedTotal());
    }

    @Test
    void consistentDirectionalTagsMatchBetweenNetworkAndResolver() {
        OsmImportResult r = way(Map.of("highway", "residential", "lanes", "4",
                "lanes:forward", "3", "lanes:backward", "1"));
        double networkFwd = forwardPermlanes(r);
        assertEquals(3.0, networkFwd, 1e-9);
        assertEquals(3, resolver().resolve(r.ways().get("10").tags(), true, false, 1.0).lanes());
    }

    @Test
    void bothWaysIsNotDuplicatedIntoNetworkLanes() {
        // lanes=5, both_ways=1 -> directional total 4 -> 2 per direction; both_ways is provenance only.
        OsmImportResult r = way(Map.of("highway", "primary", "lanes", "5", "lanes:both_ways", "1"));
        double networkLanes = forwardPermlanes(r);
        assertEquals(2.0, networkLanes, 1e-9);
        assertEquals(2, resolver().resolve(r.ways().get("10").tags(), true, false, 2.0).lanes());
    }

    @Test
    void absentLaneTagsUseRuleDefaultInNetwork() {
        // No lane tags: the network uses the rule's lanesPerDirection (residential -> 1).
        OsmImportResult r = way(Map.of("highway", "residential"));
        assertEquals(1.0, forwardPermlanes(r), 1e-9);
        // motorway rule default is 3.
        OsmImportResult m = way(Map.of("highway", "motorway"));
        assertEquals(3.0, forwardPermlanes(m), 1e-9);
    }
}
