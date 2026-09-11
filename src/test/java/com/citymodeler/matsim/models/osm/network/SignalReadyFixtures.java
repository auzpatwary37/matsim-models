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
}
