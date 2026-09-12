package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationMemberRecord;
import com.citymodeler.matsim.models.osm.OsmElementType;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/** In-memory fixtures for signal-ready simplification tests (no file parsing). */
final class SignalReadyFixtures {

    private SignalReadyFixtures() {
    }

    static OsmNodeRecord node(String id, double x, double y, String... kv) {
        Map<String, String> tags = new TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            tags.put(kv[i], kv[i + 1]);
        }
        return new OsmNodeRecord(id, 0, 0, new Coord(x, y), OsmTagSet.of(tags));
    }

    static OsmWayRecord way(String id, List<String> nodes, String... kv) {
        Map<String, String> tags = new TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            tags.put(kv[i], kv[i + 1]);
        }
        return new OsmWayRecord(id, nodes, OsmTagSet.of(tags));
    }

    static OsmRelationRecord restriction(String id, String fromWay, String viaNode, String toWay, String... kv) {
        Map<String, String> tags = new TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            tags.put(kv[i], kv[i + 1]);
        }
        return new OsmRelationRecord(id, List.of(
                new OsmRelationMemberRecord(OsmElementType.WAY, fromWay, "from"),
                new OsmRelationMemberRecord(OsmElementType.NODE, viaNode, "via"),
                new OsmRelationMemberRecord(OsmElementType.WAY, toWay, "to")),
                OsmTagSet.of(tags));
    }

    static OsmImportResult result(Map<String, OsmNodeRecord> nodes, Map<String, OsmWayRecord> ways) {
        return result(nodes, ways, Map.of());
    }

    static OsmImportResult result(Map<String, OsmNodeRecord> nodes, Map<String, OsmWayRecord> ways,
                                  Map<String, OsmRelationRecord> relations) {
        return new OsmImportResult(nodes, ways, new TreeMap<>(relations), List.of(),
                OsmProvenance.defaultFor("fixture.osm", "EPSG:3857"));
    }

    static OsmNetworkBuildResult materialize(OsmImportResult r, OsmNetworkBuildConfig cfg) {
        return new OsmMatsimNetworkBuilder().build(r, cfg);
    }

    /**
     * Crossroads: W-Wm-N-Em-E (arms 10, 20) and S-Sm-N (arm 30), all bidirectional residential.
     * N is a traffic signal. A no_left_turn relation forbids way10 -> N -> way20.
     */
    static OsmImportResult crossroads() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("W", node("W", 0, 100));
        nodes.put("Wm", node("Wm", 50, 100));
        nodes.put("N", node("N", 100, 100, "highway", "traffic_signals"));
        nodes.put("Em", node("Em", 150, 100));
        nodes.put("E", node("E", 200, 100));
        nodes.put("Sm", node("Sm", 100, 50));
        nodes.put("S", node("S", 100, 0));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", way("10", List.of("W", "Wm", "N"), "highway", "residential"));
        ways.put("20", way("20", List.of("N", "Em", "E"), "highway", "residential"));
        ways.put("30", way("30", List.of("S", "Sm", "N"), "highway", "residential"));

        Map<String, OsmRelationRecord> relations = new TreeMap<>();
        relations.put("r1", restriction("r1", "10", "N", "20",
                "type", "restriction", "restriction", "no_left_turn"));

        return result(nodes, ways, relations);
    }

    /** Crossroads with turn:lanes on the west arm (way 10) to exercise lane diagnostics. */
    static OsmImportResult crossroadsWithTurnLanes() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("W", node("W", 0, 100));
        nodes.put("Wm", node("Wm", 50, 100));
        nodes.put("N", node("N", 100, 100, "highway", "traffic_signals", "traffic_signals", "yes"));
        nodes.put("Em", node("Em", 150, 100));
        nodes.put("E", node("E", 200, 100));
        nodes.put("Sm", node("Sm", 100, 50));
        nodes.put("S", node("S", 100, 0));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", way("10", List.of("W", "Wm", "N"),
                "highway", "residential", "lanes", "2", "turn:lanes", "through|left"));
        ways.put("20", way("20", List.of("N", "Em", "E"), "highway", "residential"));
        ways.put("30", way("30", List.of("S", "Sm", "N"), "highway", "residential"));

        Map<String, OsmRelationRecord> relations = new TreeMap<>();
        relations.put("r1", restriction("r1", "10", "N", "20",
                "type", "restriction", "restriction", "no_left_turn"));

        return result(nodes, ways, relations);
    }

    /**
     * Two DISTINCT signalized intersections connected only by a normal {@code residential} street
     * of length 25 (within a 30 m threshold). Review regression: a distance-only clusterer would
     * merge them; the topology-gated one must not, because the connector is not {@code highway=link}.
     */
    static OsmImportResult twoSeparateIntersections() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("S1", node("S1", 100, 100, "highway", "traffic_signals"));
        nodes.put("S2", node("S2", 125, 100, "highway", "traffic_signals"));
        nodes.put("a1w", node("a1w", 70, 100));
        nodes.put("a1n", node("a1n", 100, 140));
        nodes.put("a2e", node("a2e", 155, 100));
        nodes.put("a2s", node("a2s", 125, 70));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", way("10", List.of("a1w", "S1"), "highway", "residential"));
        ways.put("11", way("11", List.of("a1n", "S1"), "highway", "residential"));
        ways.put("20", way("20", List.of("S2", "a2e"), "highway", "residential"));
        ways.put("21", way("21", List.of("a2s", "S2"), "highway", "residential"));
        // Normal street joining the two signalized nodes; 25 m < 30 m threshold.
        ways.put("12", way("12", List.of("S1", "S2"), "highway", "residential"));

        return result(nodes, ways);
    }

    /**
     * Three signalized intersections in a row, A-B-C, spaced 50 m apart (A-C = 100 m). Legs are
     * normal streets. Review regression: transitive distance union-find would glue A-C together via
     * B even though A-C exceeds the threshold; the topology-gated cluser must keep all three apart.
     */
    static OsmImportResult threeSignalsInARow() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("A", node("A", 100, 100, "highway", "traffic_signals"));
        nodes.put("B", node("B", 150, 100, "highway", "traffic_signals"));
        nodes.put("C", node("C", 200, 100, "highway", "traffic_signals"));
        nodes.put("an", node("an", 100, 140));
        nodes.put("bn", node("bn", 150, 140));
        nodes.put("cn", node("cn", 200, 140));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", way("10", List.of("an", "A"), "highway", "residential"));
        ways.put("11", way("11", List.of("B", "bn"), "highway", "residential"));
        ways.put("12", way("12", List.of("C", "cn"), "highway", "residential"));
        ways.put("21", way("21", List.of("A", "B"), "highway", "residential"));
        ways.put("22", way("22", List.of("B", "C"), "highway", "residential"));

        return result(nodes, ways);
    }

    /**
     * A single WIDE intersection whose two signal corners A and B are joined by a junction-internal
     * {@code highway=link} road. The arms are ordinary streets. Expected: one junction containing
     * both A and B, resolvable from either member.
     */
    static OsmImportResult oneWideIntersection(boolean internalOneway) {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("A", node("A", 100, 100, "highway", "traffic_signals"));
        nodes.put("B", node("B", 130, 100, "highway", "traffic_signals"));
        nodes.put("an", node("an", 100, 140));
        nodes.put("aw", node("aw", 60, 100));
        nodes.put("bs", node("bs", 130, 60));
        nodes.put("be", node("be", 170, 100));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", way("10", List.of("an", "A"), "highway", "residential"));
        ways.put("11", way("11", List.of("aw", "A"), "highway", "residential"));
        ways.put("12", way("12", List.of("B", "bs"), "highway", "residential"));
        ways.put("13", way("13", List.of("B", "be"), "highway", "residential"));
        if (internalOneway) {
            ways.put("90", way("90", List.of("A", "B"), "highway", "link", "oneway", "yes"));
        } else {
            ways.put("90", way("90", List.of("A", "B"), "highway", "link"));
        }

        return result(nodes, ways);
    }

    /** Wide intersection with a bidirectional internal {@code highway=link} road. */
    static OsmImportResult oneWideIntersection() {
        return oneWideIntersection(false);
    }

    /** Wide intersection with a one-way (A -> B) internal {@code highway=link} road. */
    static OsmImportResult oneWideIntersectionOneway() {
        return oneWideIntersection(true);
    }

    /**
     * Wide intersection whose two signal corners A and B are joined through a NON-signal internal
     * node X (two {@code highway=link} ways A-X and X-B). Review #2: X must survive materialization
     * and movement reachability must flow through it (A -> X -> B), not require a direct A-B link.
     */
    static OsmImportResult wideIntersectionViaInternalNode(boolean oneway) {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("A", node("A", 100, 100, "highway", "traffic_signals"));
        nodes.put("X", node("X", 115, 100));  // internal ramp/turn-lane node, NOT signalized
        nodes.put("B", node("B", 130, 100, "highway", "traffic_signals"));
        nodes.put("an", node("an", 100, 140));
        nodes.put("aw", node("aw", 60, 100));
        nodes.put("bs", node("bs", 130, 60));
        nodes.put("be", node("be", 170, 100));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", way("10", List.of("an", "A"), "highway", "residential"));
        ways.put("11", way("11", List.of("aw", "A"), "highway", "residential"));
        ways.put("12", way("12", List.of("B", "bs"), "highway", "residential"));
        ways.put("13", way("13", List.of("B", "be"), "highway", "residential"));
        if (oneway) {
            ways.put("90", way("90", List.of("A", "X"), "highway", "link", "oneway", "yes"));
            ways.put("91", way("91", List.of("X", "B"), "highway", "link", "oneway", "yes"));
        } else {
            ways.put("90", way("90", List.of("A", "X"), "highway", "link"));
            ways.put("91", way("91", List.of("X", "B"), "highway", "link"));
        }
        return result(nodes, ways);
    }

    /** Via-internal-node wide intersection, bidirectional internal path A -> X -> B. */
    static OsmImportResult wideIntersectionViaInternalNode() {
        return wideIntersectionViaInternalNode(false);
    }

    /** Via-internal-node wide intersection, one-way A -> X -> B (B -> A impossible). */
    static OsmImportResult wideIntersectionViaInternalNodeOneway() {
        return wideIntersectionViaInternalNode(true);
    }

    /**
     * Wide intersection whose only internal connectivity is a one-way path B -> X -> A, even though
     * {@code A} sorts before {@code B}. Review #3: membership must be direction-independent, so the
     * pair still clusters even though the earlier-sorting node cannot reach the later one.
     */
    static OsmImportResult wideIntersectionOnewayAgainstSort() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("A", node("A", 100, 100, "highway", "traffic_signals"));
        nodes.put("X", node("X", 115, 100));
        nodes.put("B", node("B", 130, 100, "highway", "traffic_signals"));
        nodes.put("an", node("an", 100, 140));
        nodes.put("aw", node("aw", 60, 100));
        nodes.put("bs", node("bs", 130, 60));
        nodes.put("be", node("be", 170, 100));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", way("10", List.of("an", "A"), "highway", "residential"));
        ways.put("11", way("11", List.of("aw", "A"), "highway", "residential"));
        ways.put("12", way("12", List.of("B", "bs"), "highway", "residential"));
        ways.put("13", way("13", List.of("B", "be"), "highway", "residential"));
        // One-way against the A < B sort order: B -> X -> A.
        ways.put("90", way("90", List.of("B", "X"), "highway", "link", "oneway", "yes"));
        ways.put("91", way("91", List.of("X", "A"), "highway", "link", "oneway", "yes"));
        return result(nodes, ways);
    }

    /**
     * Wide intersection whose internal road uses a REAL OSM link class ({@code primary_link}) rather
     * than the invented generic {@code highway=link}. Review #1: the production taxonomy must drive
     * both network inclusion and junction-internal recognition.
     */
    static OsmImportResult wideIntersectionPrimaryLink() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("A", node("A", 100, 100, "highway", "traffic_signals"));
        nodes.put("B", node("B", 130, 100, "highway", "traffic_signals"));
        nodes.put("an", node("an", 100, 140));
        nodes.put("aw", node("aw", 60, 100));
        nodes.put("bs", node("bs", 130, 60));
        nodes.put("be", node("be", 170, 100));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", way("10", List.of("an", "A"), "highway", "residential"));
        ways.put("11", way("11", List.of("aw", "A"), "highway", "residential"));
        ways.put("12", way("12", List.of("B", "bs"), "highway", "residential"));
        ways.put("13", way("13", List.of("B", "be"), "highway", "residential"));
        ways.put("90", way("90", List.of("A", "B"), "highway", "primary_link"));
        return result(nodes, ways);
    }

    /**
     * A single residential way with a repeated interior node (P1 at positions 1 and 3). Only the two
     * endpoints are kept, so the whole span collapses to one link whose source-segment provenance
     * must be positional (segments 0,1,2,3 forward) rather than corrupted by id -> position lookup.
     */
    static OsmImportResult wayWithRepeatedNode() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("P0", node("P0", 0, 0));
        nodes.put("P1", node("P1", 10, 0));
        nodes.put("P2", node("P2", 20, 10));
        nodes.put("P3", node("P3", 30, 0));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("50", way("50", List.of("P0", "P1", "P2", "P1", "P3"), "highway", "residential"));

        return result(nodes, ways);
    }

    /**
     * Three signalized intersections A-B-C 50 m apart, connected by junction-internal link roads:
     * A-link-B and B-link-C each qualify within a 60 m threshold, but A-link-...-C (100 m) does not.
     * Review #1: pairwise union-find would glue all three; whole-cluster validation must keep A and
     * C apart (A-B and B-C clusters, or singletons, but never {A,B,C}) because A-C does not qualify.
     */
    static OsmImportResult threeLinkedSignalsTransitiveChain() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("A", node("A", 100, 100, "highway", "traffic_signals"));
        nodes.put("B", node("B", 150, 100, "highway", "traffic_signals"));
        nodes.put("C", node("C", 200, 100, "highway", "traffic_signals"));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("21", way("21", List.of("A", "B"), "highway", "link"));
        ways.put("22", way("22", List.of("B", "C"), "highway", "link"));

        return result(nodes, ways);
    }

    /** Two adjacent collinear streets meeting at a degree-2 boundary node M with DIFFERENT lane data.
     * M must survive (shared by multiple ways) so the two links keep their distinct lane semantics.
     */
    static OsmImportResult semanticBoundaryNode() {
        Map<String, OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("L", node("L", 0, 0));
        nodes.put("M", node("M", 10, 0));
        nodes.put("R", node("R", 20, 0));

        Map<String, OsmWayRecord> ways = new TreeMap<>();
        ways.put("30", way("30", List.of("L", "M"), "highway", "residential", "lanes", "2"));
        ways.put("31", way("31", List.of("M", "R"), "highway", "residential", "lanes", "4"));

        return result(nodes, ways);
    }
}
