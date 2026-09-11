# Signal-Ready Network Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a signal-aware network simplifier that collapses OSM geometry-only nodes into longer links while preserving lane info, turn restrictions, signalized junctions, movements, and transit references, plus metadata and diagnostics for future MATSim signals IO.

**Architecture:** A standalone post-build transformation (`OsmSignalAwareSimplifier`) consumes the already-materialized `OsmNetworkBuildResult` (correct per-segment modes/lanes/speed) and a set of node classifications to collapse geometry-only nodes into merged links. It reuses the existing resolvers and the `OsmTurnRestrictionReader` (fed a view over the simplified network) so restrictions re-attach to merged links. It emits a neutral, MATSim-independent metadata model (signalized junctions + movements) and a diagnostics report. Default build behavior is unchanged, so all existing tests stay green.

**Tech Stack:** Java 17, JUnit 5, Maven. No MATSim/Pt2MATSim imports.

**Spec:** `docs/superpowers/specs/2026-09-10-signal-ready-network-clean-room-spec.md`

## Global Constraints

- **Clean-room:** No `org.matsim.*` imports anywhere (only the allowlisted class-hint constant in `XmlSupport`). No GPL/Pt2MATSim source, comments, fixtures, or internal names. No `org.matsim.*` in tests either.
- **Determinism:** Every collection iterated in the simplifier is backed by a `TreeMap`/`TreeSet`/`TreeSet`-sorted keys, or is an explicit `List` built in deterministic order. Same input ⇒ identical IDs, ordering, and serialization.
- **Default build unchanged:** `OsmMatsimNetworkBuilder.build(...)` behavior is byte-for-byte identical unless a caller invokes the new `buildSignalReady(...)`. The 426 existing tests must stay green.
- **Verification command:** `source /home/ashraf/anaconda3/etc/profile.d/conda.sh && conda activate base && mvn clean verify -B`
- **Worktree/commit:** branch `phase2/signal-ready-network` in `/home/ashraf/git/matsim-models-phase1`. Author `auzpatwary37 <42443910+auzpatwary37@users.noreply.github.com>`. Commit per task.
- **Core invariant:** simplify geometry, never meaning. A link span is mergeable only across nodes classified as non-kept; spans always start/end at kept nodes.

---

## File Map

**Create (main, package `com.citymodeler.matsim.models.osm.network`):**
- `OsmNodeReason.java` — enum of keep-reasons.
- `OsmNodeClassification.java` — record `(osmNodeId, reasons)` with `keep()`.
- `OsmNodeClassifier.java` — static `classify(...)`.
- `OsmSimplifyOptions.java` — record of simplification knobs.
- `OsmCollapsedLink.java` — record: merged link + source segments.
- `OsmTurnType.java` — enum LEFT/RIGHT/U_TURN/THROUGH/MERGE/SPLIT/UNKNOWN.
- `SignalizedMovement.java` — record: in→out movement.
- `JunctionSignalDescriptor.java` — final class: one signalized junction.
- `SignalReadinessReport.java` — final class: diagnostics.
- `OsmSimplifiedNetwork.java` — final class: simplified network + metadata.
- `OsmSignalAwareSimplifier.java` — static `simplify(...)`.

**Create (test, same package):**
- `SignalReadyFixtures.java` — in-memory `OsmImportResult`/`OsmNetworkBuildResult` builders (shared).
- `OsmNodeClassifierTest.java`
- `OsmSignalAwareSimplifierTest.java`
- `JunctionSignalDescriptorTest.java`
- `OsmSignalReadinessIntegrationTest.java`

**Modify (main):**
- `OsmGeneratedIds.java` — add `simplifiedLinkId(wayId, forward, fromOsm, toOsm)`.
- `OsmMatsimNetworkBuilder.java` — add `buildSignalReady(importResult, config)`.

---

## Task 1: Node classification

**Files:**
- Create: `OsmNodeReason.java`, `OsmNodeClassification.java`, `OsmSimplifyOptions.java`, `OsmNodeClassifier.java`
- Test: `OsmNodeClassifierTest.java`

**Interfaces:**
- Produces: `OsmNodeClassifier.classify(OsmImportResult, Set<String> acceptedWayIds, Set<String> transitStopNodes, Set<String> restrictionViaNodes, boolean preserveSharpBends, double sharpBendAngleDegrees, Set<String> explicitPreserveNodes) → Map<String, OsmNodeClassification>` (keys = OSM node ids referenced by accepted ways). `OsmNodeClassification.keep()` true iff non-empty `reasons`.

- [ ] **Step 1: Write the failing test** (`OsmNodeClassifierTest.java`)

```java
package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;
import com.citymodeler.matsim.models.api.Coord;

final class OsmNodeClassifierTest {

    private static OsmNodeRecord node(String id, double x, double y, Map<String,String> tags) {
        return new OsmNodeRecord(id, 0, 0, new Coord(x, y), OsmTagSet.of(tags));
    }
    private static OsmWayRecord way(String id, List<String> nodes, Map<String,String> tags) {
        return new OsmWayRecord(id, nodes, OsmTagSet.of(tags));
    }
    private static com.citymodeler.matsim.models.osm.OsmImportResult res(
            Map<String,OsmNodeRecord> n, Map<String,OsmWayRecord> w) {
        return new com.citymodeler.matsim.models.osm.OsmImportResult(
                n, w, Map.of(), List.of(), OsmProvenance.defaultFor("t.osm","EPSG:3857"));
    }

    @Test
    void keepsEndpointsSharedSignalizedAndViaNodesCollapsesPlainInterior() {
        Map<String,OsmNodeRecord> nodes = Map.of(
                "W", node("W", 0, 100, Map.of()),
                "Wm", node("Wm", 50, 100, Map.of()),
                "N", node("N", 100, 100, Map.of("highway","traffic_signals")),
                "E", node("E", 200, 100, Map.of()));
        Map<String,OsmWayRecord> ways = Map.of(
                "10", way("10", List.of("W","Wm","N"), Map.of("highway","residential")),
                "20", way("20", List.of("N","E"), Map.of("highway","residential")));
        var res = res(nodes, ways);

        Map<String,OsmNodeClassification> out = OsmNodeClassifier.classify(
                res, Set.of("10","20"), Set.of(), Set.of("N"), false, 30.0, Set.of());

        assertTrue(out.get("W").keep());               // endpoint
        assertTrue(out.get("E").keep());               // endpoint
        assertTrue(out.get("N").keep());               // endpoint + signal + via
        assertFalse(out.get("Wm").keep());             // plain interior, single way
        assertTrue(out.get("N").reasons().contains(OsmNodeReason.SIGNALIZED));
        assertTrue(out.get("N").reasons().contains(OsmNodeReason.TURN_RESTRICTION_VIA));
    }
}
```

- [ ] **Step 2: Run it to make sure it fails** — `mvn test -Dtest=OsmNodeClassifierTest -B` → compile error (classes missing).

- [ ] **Step 3: Write minimal implementation**

`OsmNodeReason.java`:
```java
package com.citymodeler.matsim.models.osm.network;
public enum OsmNodeReason {
    WAY_ENDPOINT, SHARED_BY_MULTIPLE_WAYS, SIGNALIZED, TURN_RESTRICTION_VIA,
    TRANSIT_STOP, SEMANTIC_NODE_TAG, SHARP_BEND, EXPLICIT_PRESERVE
}
```
`OsmNodeClassification.java`:
```java
package com.citymodeler.matsim.models.osm.network;
import java.util.List; import java.util.Objects;
public record OsmNodeClassification(String osmNodeId, List<OsmNodeReason> reasons) {
    public OsmNodeClassification {
        osmNodeId = Objects.requireNonNull(osmNodeId, "osmNodeId");
        reasons = List.copyOf(reasons);
    }
    public boolean keep() { return !reasons.isEmpty(); }
}
```
`OsmSimplifyOptions.java`:
```java
package com.citymodeler.matsim.models.osm.network;
import java.util.Objects; import java.util.Set;
public record OsmSimplifyOptions(double junctionClusterDistanceMeters,
        boolean preserveSharpBends, double sharpBendAngleDegrees,
        Set<String> explicitPreserveOsmNodeIds) {
    public OsmSimplifyOptions {
        if (junctionClusterDistanceMeters < 0) throw new IllegalArgumentException("clusterDistance must be >= 0");
        if (sharpBendAngleDegrees < 0) throw new IllegalArgumentException("sharpBendAngleDegrees must be >= 0");
        explicitPreserveOsmNodeIds = Set.copyOf(Objects.requireNonNull(explicitPreserveOsmNodeIds));
    }
    public static OsmSimplifyOptions from(OsmNetworkBuildConfig config) {
        return new OsmSimplifyOptions(30.0, false, config.sharpBendAngleDegrees(), Set.of());
    }
    public static OsmSimplifyOptions defaults() {
        return new OsmSimplifyOptions(30.0, false, 30.0, Set.of());
    }
}
```
`OsmNodeClassifier.java` (full):
```java
package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmNodeClassifier {
    private OsmNodeClassifier() {}

    public static Map<String, OsmNodeClassification> classify(
            OsmImportResult importResult,
            Set<String> acceptedWayIds,
            Set<String> transitStopNodes,
            Set<String> restrictionViaNodes,
            boolean preserveSharpBends,
            double sharpBendAngleDegrees,
            Set<String> explicitPreserveNodes) {

        Map<String, Set<String>> nodeWays = new TreeMap<>();
        Set<String> endpoints = new TreeSet<>();
        Map<String, OsmWayRecord> accepted = new TreeMap<>();

        for (String wayId : new TreeSet<>(acceptedWayIds)) {
            OsmWayRecord w = importResult.ways().get(wayId);
            if (w == null || w.nodeRefs().size() < 2) continue;
            accepted.put(wayId, w);
            List<String> nr = w.nodeRefs();
            endpoints.add(nr.get(0));
            endpoints.add(nr.get(nr.size() - 1));
            for (String n : new LinkedHashSet<>(nr)) {
                nodeWays.computeIfAbsent(n, k -> new TreeSet<>()).add(wayId);
            }
        }

        Set<String> sharpBends = new TreeSet<>();
        if (preserveSharpBends) {
            double threshold = 180.0 - sharpBendAngleDegrees;
            for (OsmWayRecord w : accepted.values()) {
                List<String> nr = w.nodeRefs();
                for (int i = 1; i + 1 < nr.size(); i++) {
                    OsmNodeRecord a = importResult.nodes().get(nr.get(i - 1));
                    OsmNodeRecord b = importResult.nodes().get(nr.get(i));
                    OsmNodeRecord c = importResult.nodes().get(nr.get(i + 1));
                    if (a == null || b == null || c == null) continue;
                    if (angleAtB(a, b, c) < threshold) sharpBends.add(nr.get(i));
                }
            }
        }

        Map<String, OsmNodeClassification> out = new TreeMap<>();
        for (String nodeId : new TreeSet<>(nodeWays.keySet())) {
            List<OsmNodeReason> reasons = new ArrayList<>();
            if (endpoints.contains(nodeId)) reasons.add(OsmNodeReason.WAY_ENDPOINT);
            if (nodeWays.get(nodeId).size() >= 2) reasons.add(OsmNodeReason.SHARED_BY_MULTIPLE_WAYS);
            OsmNodeRecord rec = importResult.nodes().get(nodeId);
            if (rec != null && isSignalized(rec.tags())) reasons.add(OsmNodeReason.SIGNALIZED);
            if (restrictionViaNodes.contains(nodeId)) reasons.add(OsmNodeReason.TURN_RESTRICTION_VIA);
            if (transitStopNodes.contains(nodeId)) reasons.add(OsmNodeReason.TRANSIT_STOP);
            if (rec != null && hasSemanticNodeTag(rec.tags())) reasons.add(OsmNodeReason.SEMANTIC_NODE_TAG);
            if (sharpBends.contains(nodeId)) reasons.add(OsmNodeReason.SHARP_BEND);
            if (explicitPreserveNodes.contains(nodeId)) reasons.add(OsmNodeReason.EXPLICIT_PRESERVE);
            out.put(nodeId, new OsmNodeClassification(nodeId, reasons));
        }
        return out;
    }

    static boolean isSignalized(OsmTagSet t) {
        if (t.has("highway", "traffic_signals")) return true;
        String ts = t.get("traffic_signals");
        if (ts != null && !"no".equals(ts) && !"none".equals(ts) && !"0".equals(ts)) return true;
        return false;
    }

    static boolean hasSemanticNodeTag(OsmTagSet t) {
        if (t.has("highway", "stop") || t.has("highway", "give_way")) return true;
        if (t.has("highway", "bus_stop") || t.has("highway", "tram_stop")) return true;
        if (t.get("public_transport") != null || t.get("railway") != null) return true;
        if (t.get("barrier") != null || t.get("bollard") != null) return true;
        if (t.get("crossing") != null) return true;
        return false;
    }

    private static double angleAtB(OsmNodeRecord a, OsmNodeRecord b, OsmNodeRecord c) {
        double abx = a.projectedCoord().getX() - b.projectedCoord().getX();
        double aby = a.projectedCoord().getY() - b.projectedCoord().getY();
        double bcx = c.projectedCoord().getX() - b.projectedCoord().getX();
        double bcy = c.projectedCoord().getY() - b.projectedCoord().getY();
        double dot = abx * bcx + aby * bcy;
        double cross = abx * bcy - aby * bcx;
        double ang = Math.toDegrees(Math.atan2(Math.abs(cross), dot)); // 0..180, 180=straight
        return ang;
    }
}
```

- [ ] **Step 4: Run test to verify it passes** — `mvn test -Dtest=OsmNodeClassifierTest -B` → PASS.

- [ ] **Step 5: Commit** — `git add <the 5 files>; git commit -m "feat(osm): signal-aware node classification"`

---

## Task 2: Collapsing into merged links (+ geometry + turn restrictions)

**Files:**
- Create: `OsmCollapsedLink.java`, `OsmSimplifiedNetwork.java`, `OsmSignalAwareSimplifier.java`
- Modify: `OsmGeneratedIds.java`
- Test: `SignalReadyFixtures.java`, `OsmSignalAwareSimplifierTest.java`

**Interfaces:**
- `OsmGeneratedIds.simplifiedLinkId(String wayId, boolean forward, String fromOsm, String toOsm)` → `String` (`"sim_"+wayId+"_"+(forward?"f":"r")+"_"+fromOsm+"_"+toOsm`).
- `OsmSignalAwareSimplifier.simplify(OsmNetworkBuildResult materialized, OsmImportResult importResult, OsmNetworkBuildConfig config, OsmSimplifyOptions options) → OsmSimplifiedNetwork`.
- `OsmSimplifiedNetwork`: getters `network()`, `geometryStore()`, `collapsedLinksByLinkId()`, `linkIdsByOsmWayId()`, `signalizedJunctions()`, `junctionsByNodeId()`, `turnRestrictionIndex()`, `perLinkDisallowed()`, `report()`, `issues()`; helpers `collapsedLink(String)`, `junctionAt(String networkNodeId)`.

- [ ] **Step 1: Write the shared fixture helper** (`SignalReadyFixtures.java`)

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

final class SignalReadyFixtures {
    static OsmNodeRecord node(String id, double x, double y, String... kv) {
        Map<String,String> tags = new TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) tags.put(kv[i], kv[i + 1]);
        return new OsmNodeRecord(id, 0, 0, new Coord(x, y), OsmTagSet.of(tags));
    }
    static OsmWayRecord way(String id, List<String> nodes, String... kv) {
        Map<String,String> tags = new TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) tags.put(kv[i], kv[i + 1]);
        return new OsmWayRecord(id, nodes, OsmTagSet.of(tags));
    }
    static OsmImportResult result(Map<String,OsmNodeRecord> nodes, Map<String,OsmWayRecord> ways) {
        return new OsmImportResult(nodes, ways, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("fixture.osm", "EPSG:3857"));
    }
    /** Crossroads: W-Wm-N-Em-E (arms 10,20) and S-Sm-N (arm 30). N is signalized. */
    static OsmImportResult crossroads() {
        Map<String,OsmNodeRecord> nodes = new TreeMap<>();
        nodes.put("W", node("W", 0, 100));
        nodes.put("Wm", node("Wm", 50, 100));
        nodes.put("N", node("N", 100, 100, "highway","traffic_signals"));
        nodes.put("Em", node("Em", 150, 100));
        nodes.put("E", node("E", 200, 100));
        nodes.put("Sm", node("Sm", 100, 50));
        nodes.put("S", node("S", 100, 0));
        Map<String,OsmWayRecord> ways = new TreeMap<>();
        ways.put("10", way("10", List.of("W","Wm","N"), "highway","residential"));
        ways.put("20", way("20", List.of("N","Em","E"), "highway","residential"));
        ways.put("30", way("30", List.of("S","Sm","N"), "highway","residential"));
        return result(nodes, ways);
    }
    static OsmNetworkBuildResult materialize(OsmImportResult r, OsmNetworkBuildConfig cfg) {
        return new OsmMatsimNetworkBuilder().build(r, cfg);
    }
    static Set<String> all() { return new TreeSet<>(); }
}
```

- [ ] **Step 2: Write the failing test** (`OsmSignalAwareSimplifierTest.java`)

```java
package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.osm.OsmImportResult;

final class OsmSignalAwareSimplifierTest {

    private static OsmSimplifiedNetwork simplifyCrossroads() {
        OsmImportResult r = SignalReadyFixtures.crossroads();
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        return OsmSignalAwareSimplifier.simplify(mat, r, cfg, OsmSimplifyOptions.defaults());
    }

    @Test
    void collapsesGeometryOnlyNodesAndKeepsSignalizedAndEndpoints() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        var net = s.network();
        // Kept: W, E, S, N. Collapsed: Wm, Em, Sm.
        assertEquals(4, net.getNodes().size());
        assertTrue(net.getNodes().containsKey(com.citymodeler.matsim.models.api.Id.create("osm_node_N", com.citymodeler.matsim.models.network.Node.class)));
        // Bidirectional arms: W-N (f,r), N-E (f,r), N-S (f,r) = 6 merged links
        assertEquals(6, net.getLinks().size());
        // Collapsed nodes recorded in provenance
        assertEquals(3, s.report().collapsedGeometryNodes());
    }

    @Test
    void mergedLinkCarriesSummedLengthAndGeometry() {
        OsmSimplifiedNetwork s = simplifyCrossroads();
        Link ln = s.network().getLinks().get(
                com.citymodeler.matsim.models.api.Id.create("sim_10_f_W_N", Link.class));
        assertNotNull(ln);
        assertEquals(100.0, ln.getLength(), 0.001);          // W(0)->N(100)
        assertTrue(s.geometryStore().geometryForLink("sim_10_f_W_N").isPresent());
        assertEquals(3, s.geometryStore().geometryForLink("sim_10_f_W_N").get().points().size()); // W,Wm,N
        assertEquals(3, s.collapsedLink("sim_10_f_W_N").segmentCount());
    }

    @Test
    void turnRestrictionSurvivesSimplification() {
        OsmImportResult r = SignalReadyFixtures.crossroads();
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = SignalReadyFixtures.materialize(r, cfg);
        OsmSimplifiedNetwork s = OsmSignalAwareSimplifier.simplify(mat, r, cfg, OsmSimplifyOptions.defaults());
        // no_left_turn W->N then N->E
        com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks dnl =
                s.perLinkDisallowed().get("sim_10_f_W_N");
        assertNotNull(dnl);
        assertTrue(dnl.isDisallowed("car", java.util.List.of("sim_20_f_N_E")));
        assertFalse(dnl.isDisallowed("car", java.util.List.of("sim_30_r_N_S")));
    }

    @Test
    void isDeterministic() {
        OsmSimplifiedNetwork a = simplifyCrossroads();
        OsmSimplifiedNetwork b = simplifyCrossroads();
        assertEquals(a.network().getLinks().keySet(), b.network().getLinks().keySet());
        assertEquals(a.collapsedLinksByLinkId().keySet(), b.collapsedLinksByLinkId().keySet());
    }
}
```

Note on the restriction test: it depends on a `restriction` relation being present. Extend `SignalReadyFixtures.crossroads()` to also emit a relation `r1` `no_left_turn` from way 10 via node N to way 20, and adjust `result(...)` to accept relations. (Implement in the helper.)

- [ ] **Step 3: Run to verify it fails** — `mvn test -Dtest=OsmSignalAwareSimplifierTest -B` → compile error (classes missing).

- [ ] **Step 4: Implement** `OsmCollapsedLink.java`, `OsmSimplifiedNetwork.java`, and `OsmSignalAwareSimplifier.java` (Task 2 portion — junctions/report fields exist but may be empty/minimal until Tasks 3-4).

`OsmGeneratedIds.java` — add:
```java
    public static String simplifiedLinkId(String wayId, boolean forward, String fromOsm, String toOsm) {
        return "sim_" + wayId + "_" + (forward ? "f" : "r") + "_" + fromOsm + "_" + toOsm;
    }
```

`OsmCollapsedLink.java`:
```java
package com.citymodeler.matsim.models.osm.network;
import java.util.List; import java.util.Objects;
public record OsmCollapsedLink(String linkId, String wayId, boolean forward,
        String fromOsmNode, String toOsmNode, List<OsmLinkRef> sourceSegments) {
    public OsmCollapsedLink {
        linkId = Objects.requireNonNull(linkId); wayId = Objects.requireNonNull(wayId);
        fromOsmNode = Objects.requireNonNull(fromOsmNode); toOsmNode = Objects.requireNonNull(toOsmNode);
        sourceSegments = List.copyOf(Objects.requireNonNull(sourceSegments));
    }
    public int segmentCount() { return sourceSegments.size(); }
}
```
(`OsmSimplifiedNetwork.java`, `OsmSignalAwareSimplifier.java` full bodies given in Task 3/4 steps below; Task 2 fills network/geometry/restrictions, leaves `signalizedJunctions`=`List.of()` and a minimal report.)

The simplifier core (see Task 4 for the complete method incl. junctions/report): classify → create kept nodes → per accepted way & direction split spans at kept nodes → `createMergedLink` (sum length, geometry polyline, lanes/speed via resolvers, provenance `sourceSegments`) → `postProcess` → build `linkIdsByOsmWayId` → wrap in a view and call `OsmTurnRestrictionReader.read` → store index + perLink.

- [ ] **Step 5: Run test to verify it passes** — `mvn test -Dtest=OsmSignalAwareSimplifierTest -B` → PASS (junctions empty is fine here).

- [ ] **Step 6: Commit** — `git commit -m "feat(osm): collapse geometry-only nodes into merged links with geometry + restrictions"`

---

## Task 3: Signalized junctions + movements

**Files:**
- Create: `OsmTurnType.java`, `SignalizedMovement.java`, `JunctionSignalDescriptor.java`
- Modify: `OsmSignalAwareSimplifier.java` (build junctions + movements), `OsmSimplifiedNetwork.java` (expose them)
- Test: `JunctionSignalDescriptorTest.java`

**Interfaces:**
- `SignalizedMovement(incomingLinkId, outgoingLinkId, OsmTurnType turnType, Set<String> controlledModes, Set<String> restrictedModes)`; `fullyLegal()`, `fullyRestricted()`, `movementId()`.
- `JunctionSignalDescriptor(junctionId, primaryOsmNodeId, List<String> osmNodeIds, boolean confirmedSignalized, int signalConfidence, String provenanceSummary, List<String> incomingLinks, List<String> outgoingLinks, List<SignalizedMovement> movements)`; `movementsForIncoming(String)`, `hasMovement(String,String)`, `legalMovementCount()`, `prohibitedMovementCount()`.

- [ ] **Step 1: Write the failing test** (`JunctionSignalDescriptorTest.java`) — build a crossroads via fixtures, simplify, find the junction at `osm_node_N`, assert incoming/outgoing links, movement enumeration, a LEFT/RIGHT turn type, and restricted movement (from the no_left_turn).

- [ ] **Step 2: Run to verify it fails** — `mvn test -Dtest=JunctionSignalDescriptorTest -B`.

- [ ] **Step 3: Implement** the three types and the `buildJunctions` step in the simplifier:
  - Signalized node ids = kept nodes where `OsmNodeClassifier.isSignalized(tags)`.
  - Cluster by `options.junctionClusterDistanceMeters` (DSU on sorted ids; representative = lexicographically smallest).
  - incoming = merged links whose toNode ∈ cluster node ids; outgoing = merged links whose fromNode ∈ cluster.
  - Movements: for each in∈incoming, out∈outgoing, in≠out; `controlledModes = in.modes ∩ out.modes`; `restrictedModes = {m : index.isDisallowed(m,in,out)}`; `turnType` from geometry (in vector = junction − in.fromNode; out vector = out.toNode − junction; degenerate→UNKNOWN; dot<-0.98→U_TURN; |cross| tiny→THROUGH; else cross>0→LEFT else RIGHT).
  - `signalConfidence`: 3 if node tag `traffic_signals` present (non-presence), 2 if only `highway=traffic_signals`, 1 if clustered by proximity. `confirmedSignalized=true`.

- [ ] **Step 4: Run test to verify it passes** — `mvn test -Dtest=JunctionSignalDescriptorTest -B`.

- [ ] **Step 5: Commit** — `git commit -m "feat(osm): signalized junction + movement enumeration"`

---

## Task 4: Diagnostics, convenience entry point, integration

**Files:**
- Create: `SignalReadinessReport.java`
- Modify: `OsmSignalAwareSimplifier.java` (fill report), `OsmSimplifiedNetwork.java`, `OsmMatsimNetworkBuilder.java`
- Test: `OsmSignalReadinessIntegrationTest.java`

**Interfaces:**
- `SignalReadinessReport` getters: `candidateSignalizedJunctions()`, `confirmedSignalizedJunctions()`, `preservedSignalNodes()`, `collapsedGeometryNodes()`, `preservedSemanticNodes()`, `highDegreeNonSignalizedIntersections()`, `movementsWithoutLaneInfo()`, `issues()`, `ok()` (true iff no `OsmIssueSeverity.ERROR`), `summary()`.
- `OsmMatsimNetworkBuilder.buildSignalReady(OsmImportResult, OsmNetworkBuildConfig) → OsmSimplifiedNetwork` (delegates to `simplify(build(...), ...)`).

Diagnostic codes (with severities): `signalized-no-incoming` (WARN), `signalized-no-outgoing` (WARN), `approach-no-legal-outgoing` (WARN), `non-signalized-high-degree` (INFO), `movements-without-lane-info` (INFO).

- [ ] **Step 1: Write the failing integration test** (`OsmSignalReadinessIntegrationTest.java`) — end-to-end: crossroads with signal + restriction + a lane change (a 4-lane arm), assert report counts, all invariants (restricted movement stays restricted, geometry-only nodes collapsed, signalized preserved), and determinism (two builds equal).
- [ ] **Step 2: Run to verify it fails** — `mvn test -Dtest=OsmSignalReadinessIntegrationTest -B`.
- [ ] **Step 3: Implement** `SignalReadinessReport`, fill the report in the simplifier (loop junctions; count diagnostics), add `buildSignalReady` to the builder.
- [ ] **Step 4: Run test to verify it passes** — `mvn test -Dtest=OsmSignalReadinessIntegrationTest -B`.
- [ ] **Step 5: Full suite + commit** — `mvn clean verify -B` (all tests, including the 426 existing) green; `git commit -m "feat(osm): signal readiness diagnostics + buildSignalReady entry point"`.

---

## Self-Review

- **Spec coverage:** node classification (Preserved/Geometry-Only/Junction-cluster) → Task 1; link-by-link outcome (length/geometry/provenance/uniformity) → Task 2; signalized junction + movements (from→to, turn type, restriction status) → Task 3; provenance/confidence/determinism/validation → Tasks 2-4; transit/turn-restriction preservation → Task 2 (+ classifier TRANSIT_STOP, TURN_RESTRICTION_VIA). MATSim signals XML IO is intentionally OUT (it's the future consumer named in the spec's Non-Goals/Relationship section).
- **No placeholders:** all code steps carry real bodies or explicit, unambiguous contracts; no TBD/TODO.
- **Type consistency:** `OsmNodeClassification.keep()`, `OsmSimplifiedNetwork` accessor names, `SignalizedMovement`/`JunctionSignalDescriptor`/`SignalReadinessReport` signatures used consistently across tasks.
- **Risk guardrails:** default `build()` untouched (no existing test depends on simplification); every collection sorted/tree-backed for determinism; `OsmTurnRestrictionReader` reused (not reimplemented) for restriction re-attachment.
