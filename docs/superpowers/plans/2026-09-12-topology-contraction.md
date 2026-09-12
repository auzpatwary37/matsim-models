# OSM Topology Contraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make OSM import produce a compact, routable MATSim network by contracting degree-2 geometry/way-split nodes — across OSM way boundaries — instead of materializing every accepted OSM node, while preserving full source polylines and semantic nodes.

**Architecture:** Introduce a shared `OsmSegmentGraph` (atomic OSM node-pair edges with routing attributes), an `OsmRoutingNodeSelector` that decides which nodes are true routing nodes from intrinsic reasons plus structural degree/property-change rules, and an `OsmTopologyBuilder` that dissolves non-routing degree-2 nodes and emits merged links spanning multiple OSM ways. Both `OsmMatsimNetworkBuilder.build()` (for `PRESERVE_AS_LINK_GEOMETRY` / `ROUTING_NODES_ONLY`) and `OsmSignalAwareSimplifier.simplify()` (signal-ready) consume the same engine so their topology cannot diverge.

**Tech Stack:** Java 17, Maven, JUnit 5, proj4j (existing). No MATSim / pt2MATSim code or bytecode; clean-room only (own spec + public formats + black-box output files).

**Spec:** `docs/superpowers/specs/2026-09-08-gtfs-transit-mapping-clean-room-design.md` (§"Geometry Preservation: Shape Nodes Vs Routing Nodes", §"Phase 1E"), `docs/superpowers/specs/2026-09-10-signal-ready-network-clean-room-spec.md`, and `docs/pt2matsim-blackbox-comparison.md` (measured motivation).

## Global Constraints

- Clean-room: never read, decompile, `javap`, or derive algorithms from pt2MATSim/MATSim source or bytecode. Allowed: this repo's specs, published XSD/DTD, black-box output artifacts, prior knowledge. Only permitted `org.matsim.*` string is the existing `DisallowedNextLinks` class hint.
- Apache-2.0 original code only; no new third-party runtime dependencies.
- Determinism: all traversal/collections sorted or insertion-ordered; identical input ⇒ byte-identical output.
- Do NOT change `highway=service` / `busway` default scope in this plan. Fix over-segmentation first; measure by class afterward (Task 8).
- `MATERIALIZE_GEOMETRY_NODES` must keep today's one-node-per-OSM-node behavior (visualization mode).
- Default geometry mode stays `PRESERVE_AS_LINK_GEOMETRY`.
- Existing public types `Network`, `Link`, `Node`, `Coord`, `OsmImportResult`, `OsmWayRule`, `OsmGeometryStore`, `OsmPolyline` keep their current shapes.
- Build/test command: `mvn -o -B test -Dtest='<TestClass>'`; full gate `mvn -o clean verify`.

---

### Task 1: Atomic segment graph

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmSegmentGraph.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmSegmentGraphTest.java`

**Interfaces:**
- Consumes: `OsmImportResult`, `OsmNetworkBuildConfig`, `OsmWayRule`, `OsmModeAccessResolver.DirectionDecision`, `OsmSpeedResolver`, `OsmLaneResolver`, `OsmNodeRecord`.
- Produces:
  - `record OsmSegmentGraph.Segment(String wayId, int segmentIndex, String nodeA, String nodeB, Set<String> forwardModes, Set<String> backwardModes, double forwardSpeed, double backwardSpeed, double forwardLanes, double backwardLanes, double capacityPerLane, boolean forwardAllowed, boolean backwardAllowed)` — **directional**: fields are per-direction, accessed via `modes(boolean forward)` / `speed(boolean forward)` / `lanes(boolean forward)`; mode sets are stored as `Collections.unmodifiableSet(new TreeSet<>(...))`.
  - `static OsmSegmentGraph build(OsmImportResult, OsmNetworkBuildConfig)`
  - `List<Segment> segmentsFrom(String osmNodeId)`
  - `Set<String> nodeIds()`
  - `List<Segment> segments()`
  - `String other(Segment s, String osmNodeId)`
  - `boolean allowsTravel(Segment s, String from, String to)`

- [ ] **Step 1: Write the failing test**

`OsmSegmentGraphTest.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;
import com.citymodeler.matsim.models.api.Coord;

class OsmSegmentGraphTest {

    private static OsmNodeRecord node(String id, double x) {
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.empty());
    }

    private static OsmImportResult result(Map<String, OsmNodeRecord> nodes,
                                          Map<String, OsmWayRecord> ways) {
        return new OsmImportResult(nodes, ways, new java.util.TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("fixture.osm", "EPSG:3857"));
    }

    @Test
    void buildsOneSegmentPerConsecutivePair() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", new OsmWayRecord("10", List.of("A", "B", "C"),
                OsmTagSet.of(Map.of("highway", "residential"))));

        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways),
                OsmNetworkBuildConfig.materializeGeometryConfig());

        assertEquals(2, g.segments().size());
        assertEquals(Set.of("A", "B", "C"), g.nodeIds());
        assertEquals(2, g.segmentsFrom("B").size());
        assertEquals(1, g.segmentsFrom("A").size());
    }

    @Test
    void onewaySegmentAllowsOnlyForwardTravel() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("highway", "residential", "oneway", "yes"))));

        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways),
                OsmNetworkBuildConfig.materializeGeometryConfig());
        OsmSegmentGraph.Segment s = g.segments().get(0);

        assertTrue(s.forwardAllowed());
        assertFalse(s.backwardAllowed());
        assertTrue(g.allowsTravel(s, "A", "B"));
        assertFalse(g.allowsTravel(s, "B", "A"));
    }

    /** Same road split at a degree-2 node yields two consecutive atomic segments. */
    @Test
    void splitRoadYieldsTwoConsecutiveSegments() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("highway", "residential"))));
        ways.put("11", new OsmWayRecord("11", List.of("B", "C"),
                OsmTagSet.of(Map.of("highway", "residential"))));

        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways),
                OsmNetworkBuildConfig.materializeGeometryConfig());

        assertEquals(2, g.segments().size());
        assertEquals(2, g.segmentsFrom("B").size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -B test -Dtest=OsmSegmentGraphTest`
Expected: FAIL — `OsmSegmentGraph` does not exist (compile error).

- [ ] **Step 3: Write minimal implementation**

`OsmSegmentGraph.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.*;

import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Atomic segment graph: one undirected edge per consecutive OSM node pair of an accepted way, each
 * carrying the resolved routing attributes of that pair and its allowed travel directions. Built
 * once and shared by the topology builder and the signal-aware simplifier so their topology cannot
 * diverge. Pure data; no MATSim types.
 */
public final class OsmSegmentGraph {

    /** One consecutive OSM node pair of a way, with resolved DIRECTIONAL routing attributes. */
    public record Segment(
            String wayId, int segmentIndex, String nodeA, String nodeB,
            Set<String> forwardModes, Set<String> backwardModes,
            double forwardSpeed, double backwardSpeed,
            double forwardLanes, double backwardLanes, double capacityPerLane,
            boolean forwardAllowed, boolean backwardAllowed) {
        public Segment {
            Objects.requireNonNull(wayId, "wayId");
            Objects.requireNonNull(nodeA, "nodeA");
            Objects.requireNonNull(nodeB, "nodeB");
            forwardModes = Collections.unmodifiableSet(new TreeSet<>(forwardModes));
            backwardModes = Collections.unmodifiableSet(new TreeSet<>(backwardModes));
        }

        /** Modes permitted in the given travel direction (nodeA->nodeB when {@code forward}). */
        public Set<String> modes(boolean forward) {
            return forward ? forwardModes : backwardModes;
        }

        /** Free speed in the given travel direction (nodeA->nodeB when {@code forward}). */
        public double speed(boolean forward) {
            return forward ? forwardSpeed : backwardSpeed;
        }

        /** Lanes in the given travel direction (nodeA->nodeB when {@code forward}). */
        public double lanes(boolean forward) {
            return forward ? forwardLanes : backwardLanes;
        }
    }

    private final List<Segment> segments;
    private final Map<String, List<Segment>> byNode;

    private OsmSegmentGraph(List<Segment> segments) {
        this.segments = List.copyOf(segments);
        Map<String, List<Segment>> map = new TreeMap<>();
        for (Segment s : segments) {
            map.computeIfAbsent(s.nodeA(), k -> new ArrayList<>()).add(s);
            if (!s.nodeB().equals(s.nodeA())) {
                map.computeIfAbsent(s.nodeB(), k -> new ArrayList<>()).add(s);
            }
        }
        this.byNode = map;
    }

    public static OsmSegmentGraph build(OsmImportResult importResult, OsmNetworkBuildConfig config) {
        List<Segment> out = new ArrayList<>();
        OsmModeAccessResolver access = new OsmModeAccessResolver();
        OsmSpeedResolver speed = new OsmSpeedResolver();
        OsmLaneResolver lanes = new OsmLaneResolver();

        for (OsmWayRecord way : importResult.ways().values()) {
            OsmWayRule rule = config.resolveRule(way.tags());
            if (rule == null) {
                continue;
            }
            List<String> refs = way.nodeRefs();
            if (refs.size() < 2) {
                continue;
            }
            List<OsmModeAccessResolver.DirectionDecision> decisions = access.resolve(way, rule.allowedModes());
            for (int i = 0; i + 1 < refs.size(); i++) {
                String a = refs.get(i);
                String b = refs.get(i + 1);
                if (importResult.nodes().get(a) == null || importResult.nodes().get(b) == null) {
                    continue;
                }
                Set<String> forwardModes = new TreeSet<>();
                Set<String> backwardModes = new TreeSet<>();
                boolean fwd = false;
                boolean bwd = false;
                for (OsmModeAccessResolver.DirectionDecision d : decisions) {
                    if (d.allowedModes().isEmpty()) {
                        continue;
                    }
                    if (d.forward()) { fwd = true; forwardModes.addAll(d.allowedModes()); }
                    if (d.backward()) { bwd = true; backwardModes.addAll(d.allowedModes()); }
                }
                if (forwardModes.isEmpty() && backwardModes.isEmpty()) {
                    continue;
                }
                boolean oneway = !(fwd && bwd);
                double forwardSpeed = speed.resolve(way, rule, true);
                double backwardSpeed = speed.resolve(way, rule, false);
                double forwardLanes = lanes.resolve(way, rule, true, oneway);
                double backwardLanes = lanes.resolve(way, rule, false, oneway);
                out.add(new Segment(way.id(), i, a, b,
                        forwardModes, backwardModes, forwardSpeed, backwardSpeed,
                        forwardLanes, backwardLanes, rule.capacityPerLane(), fwd, bwd));
            }
        }
        out.sort(Comparator.comparing((Segment s) -> s.wayId())
                .thenComparingInt(Segment::segmentIndex));
        return new OsmSegmentGraph(out);
    }

    public List<Segment> segments() {
        return segments;
    }

    public Set<String> nodeIds() {
        return Collections.unmodifiableSet(byNode.keySet());
    }

    public List<Segment> segmentsFrom(String osmNodeId) {
        return Collections.unmodifiableList(byNode.getOrDefault(osmNodeId, List.of()));
    }

    public String other(Segment s, String osmNodeId) {
        if (s.nodeA().equals(osmNodeId)) return s.nodeB();
        if (s.nodeB().equals(osmNodeId)) return s.nodeA();
        throw new IllegalArgumentException("node " + osmNodeId + " not an endpoint of " + s);
    }

    /** True when the segment permits travel in the given directed endpoint order. */
    public static boolean allowsTravel(Segment s, String from, String to) {
        if (s.nodeA().equals(from) && s.nodeB().equals(to)) return s.forwardAllowed();
        if (s.nodeB().equals(from) && s.nodeA().equals(to)) return s.backwardAllowed();
        return false;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -o -B test -Dtest=OsmSegmentGraphTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/osm/network/OsmSegmentGraph.java \
        src/test/java/com/citymodeler/matsim/models/osm/network/OsmSegmentGraphTest.java
git commit -m "feat(osm): atomic segment graph for topology contraction"
```

---

### Task 2: Routing-node selection (intrinsic reasons + structural rules)

**Files:**
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNodeReason.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNodeClassifier.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmRoutingNodeSelector.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkBuildConfig.java` (add `preserveCrossingNodes`, `preserveBarrierNodes`)
- Modify tests: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmNodeClassifierTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmRoutingNodeSelectorTest.java`

**Interfaces:**
- Consumes: `OsmSegmentGraph`, `OsmImportResult`, `OsmNetworkBuildConfig`, `OsmNodeClassification`.
- Produces:
  - `OsmNodeReason` values: `SIGNALIZED, TURN_RESTRICTION_VIA, TRANSIT_STOP, BARRIER, CROSSING, SHARP_BEND, EXPLICIT_PRESERVE, SEMANTIC_NODE_TAG` (removed: `WAY_ENDPOINT`, `SHARED_BY_MULTIPLE_WAYS`).
  - `static Map<String, OsmNodeClassification> OsmNodeClassifier.classifyIntrinsic(OsmImportResult, Set<String> acceptedWayIds, Set<String> transitStopNodes, Set<String> restrictionViaNodes, boolean preserveSharpBends, double sharpBendAngleDegrees, Set<String> explicitPreserveNodes, boolean preserveCrossingNodes, boolean preserveBarrierNodes)` (the legacy `classify(...)` 7/8-arg entry point was deleted in Task 5; Task 6 added the 9th `preserveBarrierNodes` boolean)
  - `static Set<String> OsmRoutingNodeSelector.select(OsmSegmentGraph graph, Map<String, OsmNodeClassification> classification, boolean keepAll)`
  - `static boolean contractible(OsmSegmentGraph g, String nodeId)`

- [ ] **Step 1: Write the failing test**

`OsmRoutingNodeSelectorTest.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

class OsmRoutingNodeSelectorTest {

    private static OsmNodeRecord node(String id, double x, String... kv) {
        Map<String, String> t = new java.util.TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) t.put(kv[i], kv[i + 1]);
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.of(t));
    }

    private static OsmImportResult result(Map<String, OsmNodeRecord> nodes, Map<String, OsmWayRecord> ways) {
        return new OsmImportResult(nodes, ways, new java.util.TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));
    }

    private static OsmWayRecord way(String id, List<String> refs, String... kv) {
        Map<String, String> t = new java.util.TreeMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) t.put(kv[i], kv[i + 1]);
        return new OsmWayRecord(id, refs, OsmTagSet.of(t));
    }

    /** The core invariant: a plain degree-2 node is contractible whether or not it splits a way. */
    @Test
    void plainDegree2NodeIsContractibleAcrossWays() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100)); // degree 2, no tags, way boundary
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential"));
        ways.put("11", way("11", List.of("B", "C"), "highway", "residential"));

        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);

        assertTrue(OsmRoutingNodeSelector.contractible(g, "B"));
        assertEquals(Set.of("A", "C"), OsmRoutingNodeSelector.select(g, cls, false));
    }

    /** A node where speed changes must be kept so the two links can differ. */
    @Test
    void propertyChangeNodeIsKept() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential", "maxspeed", "30"));
        ways.put("11", way("11", List.of("B", "C"), "highway", "residential", "maxspeed", "50"));

        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);

        assertFalse(OsmRoutingNodeSelector.contractible(g, "B"));
        assertTrue(OsmRoutingNodeSelector.select(g, cls, false).contains("B"));
    }

    /** A signal at a degree-2 node is always retained. */
    @Test
    void signalNodeIsKept() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100, "highway", "traffic_signals"));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential"));
        ways.put("11", way("11", List.of("B", "C"), "highway", "residential"));

        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);

        assertFalse(OsmRoutingNodeSelector.contractible(g, "B"));
    }

    /** A degree-2 U-shaped oneway node (directions oppose) must not contract. */
    @Test
    void opposingOnewayDegree2NodeIsKept() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        nodes.put("C", node("C", 100, "y", "1"));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential", "oneway", "yes"));
        ways.put("11", way("11", List.of("C", "B"), "highway", "residential", "oneway", "yes"));

        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);

        assertFalse(OsmRoutingNodeSelector.contractible(g, "B"));
    }

    @Test
    void keepAllReturnsEveryNode() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential"));
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        assertEquals(Set.of("A", "B"), OsmRoutingNodeSelector.select(g, Map.of(), true));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -B test -Dtest=OsmRoutingNodeSelectorTest`
Expected: FAIL — `OsmRoutingNodeSelector` missing / `classify` arity mismatch.

- [ ] **Step 3: Write minimal implementation**

`OsmNodeReason.java` (replace body):

```java
package com.citymodeler.matsim.models.osm.network;

/** Intrinsic reasons an OSM node must be preserved as a routing node (structural degree is separate). */
public enum OsmNodeReason {
    SIGNALIZED,
    TURN_RESTRICTION_VIA,
    TRANSIT_STOP,
    BARRIER,
    CROSSING,
    SHARP_BEND,
    EXPLICIT_PRESERVE,
    SEMANTIC_NODE_TAG
}
```

`OsmNodeClassifier.classifyIntrinsic` — intrinsic-only entry point; change the signature to add `boolean preserveCrossingNodes`, and emit only intrinsic reasons (remove `WAY_ENDPOINT` and `SHARED_BY_MULTIPLE_WAYS`). Task 6 later appends a 9th `boolean preserveBarrierNodes`; the legacy `classify(...)` name/arity is gone:

```java
    public static Map<String, OsmNodeClassification> classifyIntrinsic(
            OsmImportResult importResult,
            Set<String> acceptedWayIds,
            Set<String> transitStopNodes,
            Set<String> restrictionViaNodes,
            boolean preserveSharpBends,
            double sharpBendAngleDegrees,
            Set<String> explicitPreserveNodes,
            boolean preserveCrossingNodes,
            boolean preserveBarrierNodes) {
```

Inside the per-node loop replace the reason block with:

```java
            List<OsmNodeReason> reasons = new ArrayList<>();
            OsmNodeRecord rec = importResult.nodes().get(nodeId);
            if (rec != null && isSignalized(rec.tags())) {
                reasons.add(OsmNodeReason.SIGNALIZED);
            }
            if (restrictionViaNodes.contains(nodeId)) {
                reasons.add(OsmNodeReason.TURN_RESTRICTION_VIA);
            }
            if (transitStopNodes.contains(nodeId)) {
                reasons.add(OsmNodeReason.TRANSIT_STOP);
            }
            if (rec != null && isBarrier(rec.tags())) {
                reasons.add(OsmNodeReason.BARRIER);
            }
            if (preserveCrossingNodes && rec != null && rec.tags().get("crossing") != null) {
                reasons.add(OsmNodeReason.CROSSING);
            }
            if (rec != null && hasSemanticNodeTag(rec.tags())) {
                reasons.add(OsmNodeReason.SEMANTIC_NODE_TAG);
            }
            if (sharpBends.contains(nodeId)) {
                reasons.add(OsmNodeReason.SHARP_BEND);
            }
            if (explicitPreserveNodes.contains(nodeId)) {
                reasons.add(OsmNodeReason.EXPLICIT_PRESERVE);
            }
            out.put(nodeId, new OsmNodeClassification(nodeId, reasons));
```

Update `hasSemanticNodeTag` to no longer force a keep for crossings (crossing handled by `preserveCrossingNodes`); keep stop/barrier/railway. Add:

```java
    static boolean isBarrier(OsmTagSet t) {
        return t.get("barrier") != null || t.get("bollard") != null;
    }
```

`OsmNetworkBuildConfig` — add two fields and accessors `preserveCrossingNodes()` / `preserveBarrierNodes()`, defaulting `false` / `true`, threaded through the private constructor and both factory methods (`materializeGeometryConfig` and `defaultConfig`). Keep the existing 5-arg constructor behavior by defaulting the new ones.

`OsmRoutingNodeSelector.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.*;

/**
 * Decides which OSM nodes remain MATSim routing nodes. A node is kept when it carries an intrinsic
 * reason (signal, turn-restriction via, stop, barrier, crossing [config], semantic tag, sharp bend,
 * explicit preserve), when its true undirected degree is not 2, when the two incident atomic segments
 * are not contractible (different attributes or incompatible directions), or when {@code keepAll}.
 *
 * <p>Contraction is deliberately independent of OSM way boundaries: a degree-2 node is dissolved
 * whether it is mid-way or a way split, so equivalent physical roads produce identical topology.
 */
public final class OsmRoutingNodeSelector {

    private OsmRoutingNodeSelector() {
    }

    public static Set<String> select(OsmSegmentGraph graph,
                                     Map<String, OsmNodeClassification> classification,
                                     boolean keepAll) {
        Set<String> keep = new TreeSet<>();
        for (String nodeId : graph.nodeIds()) {
            if (keepAll) {
                keep.add(nodeId);
                continue;
            }
            OsmNodeClassification c = classification.get(nodeId);
            if (c != null && c.keep()) {
                keep.add(nodeId);
                continue;
            }
            if (!contractible(graph, nodeId)) {
                keep.add(nodeId);
            }
        }
        return keep;
    }

    /** True when the node is a safe pass-through: exactly two compatible incident segments. */
    public static boolean contractible(OsmSegmentGraph graph, String nodeId) {
        List<OsmSegmentGraph.Segment> incident = graph.segmentsFrom(nodeId);
        if (incident.size() != 2) {
            return false;
        }
        OsmSegmentGraph.Segment s1 = incident.get(0);
        OsmSegmentGraph.Segment s2 = incident.get(1);
        if (!compatible(s1, s2)) {
            return false;
        }
        String p = graph.other(s1, nodeId);
        String q = graph.other(s2, nodeId);
        if (p.equals(q)) {
            return false; // a self-loop pair
        }
        boolean pToQ = OsmSegmentGraph.allowsTravel(s1, p, nodeId)
                && OsmSegmentGraph.allowsTravel(s2, nodeId, q);
        boolean qToP = OsmSegmentGraph.allowsTravel(s2, q, nodeId)
                && OsmSegmentGraph.allowsTravel(s1, nodeId, p);
        return pToQ || qToP;
    }

    private static boolean compatible(OsmSegmentGraph.Segment a, OsmSegmentGraph.Segment b) {
        return a.forwardModes().equals(b.forwardModes())
                && a.backwardModes().equals(b.backwardModes())
                && Double.compare(a.forwardSpeed(), b.forwardSpeed()) == 0
                && Double.compare(a.backwardSpeed(), b.backwardSpeed()) == 0
                && Double.compare(a.forwardLanes(), b.forwardLanes()) == 0
                && Double.compare(a.backwardLanes(), b.backwardLanes()) == 0
                && Double.compare(a.capacityPerLane(), b.capacityPerLane()) == 0
                && a.forwardAllowed() == b.forwardAllowed()
                && a.backwardAllowed() == b.backwardAllowed();
    }
}
```

- [ ] **Step 4: Update `OsmNodeClassifierTest` for the new contract**

The existing test `keepsEndpointsSharedSignalizedAndViaNodesCollapsesPlainInterior` asserted `W`, `E` (way endpoints) and `N` (shared) keep via the *classifier*. Endpoint/shared are no longer intrinsic reasons. Change those assertions to assert the intrinsic reasons only, and move structural keep assertions to `OsmRoutingNodeSelectorTest`:

```java
        // W/E/N are all degree-2+ in the selector; the classifier now reports intrinsic reasons only.
        assertTrue(out.get("N").reasons().contains(OsmNodeReason.SIGNALIZED));
        assertTrue(out.get("N").reasons().contains(OsmNodeReason.TURN_RESTRICTION_VIA));
        assertFalse(out.get("W").reasons().contains(OsmNodeReason.SIGNALIZED));
        assertTrue(out.get("Wm").reasons().isEmpty());
```

Also update every `OsmNodeClassifier.classifyIntrinsic(...)` call in tests to the new 8-arg form (append `false`), and any production call (Task 5 does the simplifier call). (Task 6 appends the 9th `preserveBarrierNodes` argument, making the final call 9-arg.)

- [ ] **Step 5: Run tests**

Run: `mvn -o -B test -Dtest='OsmRoutingNodeSelectorTest,OsmNodeClassifierTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(osm): intrinsic-only node reasons + structural routing-node selector"
```

---

### Task 3: Cross-way contraction engine + multi-way collapsed link

**Files:**
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmCollapsedLink.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmGeneratedIds.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/CollapsedTopology.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmTopologyBuilder.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmTopologyBuilderTest.java`

**Interfaces:**
- Consumes: `OsmSegmentGraph`, `OsmRoutingNodeSelector`, `OsmImportResult`, `OsmNetworkBuildConfig`, `OsmWayRule`, `Network`.
- Produces:
  - `record OsmCollapsedLink(String linkId, boolean forward, String fromOsmNode, String toOsmNode, List<OsmLinkRef> sourceSegments)` with derived `List<String> sourceOsmWayIds()` and `int segmentCount()`; no singular `wayId`.
  - `record CollapsedTopology(Network network, Map<String, OsmCollapsedLink> collapsedLinksByLinkId, Map<String, List<String>> linkIdsByOsmWayId, Map<String, OsmPolyline> geometry, Map<String, OsmNodeClassification> classification, Set<String> routingNodeIds, List<OsmImportIssue> issues)`.
  - `static CollapsedTopology build(OsmImportResult, OsmNetworkBuildConfig, boolean keepAllGeometryNodes)` — structural classification only (no signal/stop/via extraction); used by plain `build()` and unit tests.
  - `static CollapsedTopology buildSignalReady(OsmImportResult, OsmNetworkBuildConfig, OsmSimplifyOptions)` — extracts stop/via nodes, honors `OsmSimplifyOptions.preserveSharpBends()`, angle, and explicit preserves; always contracts (`keepAllGeometryNodes=false`).
  - `OsmGeneratedIds.simplifiedLinkId(String firstWayId, boolean forward, String fromOsm, String toOsm)` (kept; first source way id used).

- [ ] **Step 1: Write the failing test**

`OsmTopologyBuilderTest.java` — includes **the key invariance oracle**:

```java
package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

class OsmTopologyBuilderTest {

    private static OsmNodeRecord n(String id, double x) {
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.empty());
    }
    private static OsmWayRecord w(String id, List<String> refs) {
        return new OsmWayRecord(id, refs, OsmTagSet.of(Map.of("highway", "residential")));
    }
    private static OsmImportResult res(Map<String, OsmNodeRecord> ns, Map<String, OsmWayRecord> ws) {
        return new OsmImportResult(ns, ws, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));
    }
    /** Topology signature: sorted "from->to" for every link, independent of ids. */
    private static Set<String> signature(Network net) {
        Set<String> sig = new TreeSet<>();
        net.getLinks().values().forEach(l ->
                sig.add(l.getFromNode().getId() + "->" + l.getToNode().getId()));
        return sig;
    }

    @Test
    void collapsesPlainDegree2NodeIntoOneLink() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B", "C")));

        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false);

        assertEquals(Set.of("osm_node_A", "osm_node_C"), t.network().getNodes().keySet().stream()
                .map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, t.network().getLinks().size()); // forward + reverse
        assertTrue(t.geometry().containsKey("sim_10_f_A_C"));
        assertEquals(3, t.geometry().get("sim_10_f_A_C").points().size()); // A,B,C preserved
    }

    /**
     * THE invariant: the same physical road represented as one way and as two ways split at a
     * degree-2 node must produce identical MATSim topology after contraction.
     */
    @Test
    void oneWayAndSplitWayProduceIdenticalTopology() {
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();

        Map<String, OsmNodeRecord> ns1 = new TreeMap<>();
        ns1.put("A", n("A", 0)); ns1.put("B", n("B", 100)); ns1.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws1 = new TreeMap<>();
        ws1.put("10", w("10", List.of("A", "B", "C")));

        Map<String, OsmNodeRecord> ns2 = new TreeMap<>();
        ns2.put("A", n("A", 0)); ns2.put("B", n("B", 100)); ns2.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws2 = new TreeMap<>();
        ws2.put("10", w("10", List.of("A", "B")));
        ws2.put("11", w("11", List.of("B", "C")));

        Network one = OsmTopologyBuilder.build(res(ns1, ws1), cfg, false).network();
        Network two = OsmTopologyBuilder.build(res(ns2, ws2), cfg, false).network();

        assertEquals(one.getNodes().size(), two.getNodes().size());
        assertEquals(one.getLinks().size(), two.getLinks().size());
        assertEquals(signature(one), signature(two));
    }

    @Test
    void crossWayCollapsedLinkListsBothSourceWays() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B")));
        ws.put("11", w("11", List.of("B", "C")));

        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), false);
        OsmCollapsedLink cl = t.collapsedLinksByLinkId().get("sim_10_f_A_C");
        assertNotNull(cl);
        assertEquals(List.of("10", "11"), cl.sourceOsmWayIds());
        assertEquals(2, cl.segmentCount());
        // the merged link is indexed under BOTH source ways
        assertTrue(t.linkIdsByOsmWayId().get("10").contains("sim_10_f_A_C"));
        assertTrue(t.linkIdsByOsmWayId().get("11").contains("sim_10_f_A_C"));
    }

    @Test
    void keepAllModeDoesNotCollapse() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100)); ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B", "C")));
        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws),
                OsmNetworkBuildConfig.materializeGeometryConfig(), true);
        assertEquals(3, t.network().getNodes().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -B test -Dtest=OsmTopologyBuilderTest`
Expected: FAIL — types missing.

- [ ] **Step 3: Write minimal implementation**

`OsmCollapsedLink.java` (replace):

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.*;

/** A merged MATSim link produced by contracting one or more atomic OSM segments, possibly spanning multiple OSM ways. */
public record OsmCollapsedLink(
        String linkId,
        boolean forward,
        String fromOsmNode,
        String toOsmNode,
        List<OsmLinkRef> sourceSegments) {

    public OsmCollapsedLink {
        linkId = Objects.requireNonNull(linkId, "linkId");
        fromOsmNode = Objects.requireNonNull(fromOsmNode, "fromOsmNode");
        toOsmNode = Objects.requireNonNull(toOsmNode, "toOsmNode");
        sourceSegments = List.copyOf(Objects.requireNonNull(sourceSegments, "sourceSegments"));
        if (sourceSegments.isEmpty()) {
            throw new IllegalArgumentException("sourceSegments must not be empty");
        }
    }

    public int segmentCount() {
        return sourceSegments.size();
    }

    /** Distinct source OSM way ids in segment order (a merged link may span several ways). */
    public List<String> sourceOsmWayIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (OsmLinkRef r : sourceSegments) {
            ids.add(r.osmWayId());
        }
        return List.copyOf(ids);
    }

    /** The way id of the first source segment; used for deterministic link-id derivation. */
    public String firstOsmWayId() {
        return sourceSegments.get(0).osmWayId();
    }
}
```

`CollapsedTopology.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.*;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;

/** Result of topology contraction: compact network + provenance + geometry + classification. */
public record CollapsedTopology(
        Network network,
        Map<String, OsmCollapsedLink> collapsedLinksByLinkId,
        Map<String, List<String>> linkIdsByOsmWayId,
        Map<String, OsmPolyline> geometry,
        Map<String, OsmNodeClassification> classification,
        Set<String> routingNodeIds,
        List<OsmImportIssue> issues) {
}
```

`OsmTopologyBuilder.build` — the engine for the plain path. `build(importResult, config, keepAllGeometryNodes)` builds the graph, classifies **structural + intrinsic node tags only** (no signal/stop/via extraction), selects routing nodes (forcing keep-all when `keepAllGeometryNodes`), creates a `Network` node per routing node, and for every maximal chain between routing nodes emits one link per allowed direction with summed length + concatenated geometry + source segments; writes `OsmCollapsedLink` indexed under every source way; `network.postProcess()`; anchors the smallest node of any routing-node-free cycle. Reference sketch:

```java
    public static CollapsedTopology build(OsmImportResult importResult,
                                          OsmNetworkBuildConfig config,
                                          boolean keepAllGeometryNodes) {
        List<OsmImportIssue> issues = new ArrayList<>(importResult.issues());
        Set<String> acceptedWayIds = new TreeSet<>();
        for (OsmWayRecord w : importResult.ways().values()) {
            if (config.resolveRule(w.tags()) != null && w.nodeRefs().size() >= 2) {
                acceptedWayIds.add(w.id());
            }
        }
        OsmSegmentGraph graph = OsmSegmentGraph.build(importResult, config);
        Set<String> stopNodes = new TreeSet<>();
        Set<String> viaNodes = new TreeSet<>();
        // Via-way chains: retain every node of a via way so the chain's interior survives.
        for (OsmRelationRecord rel : importResult.relations().values()) {
            String type = rel.tags().get("type");
            if (type == null || !type.startsWith("restriction")) continue;
            for (OsmRelationMemberRecord mm : rel.members()) {
                if (!"via".equals(mm.role())) continue;
                if (mm.type() == OsmElementType.NODE) viaNodes.add(mm.ref());
                else if (mm.type() == OsmElementType.WAY) {
                    OsmWayRecord viaWay = importResult.ways().get(mm.ref());
                    if (viaWay != null) viaNodes.addAll(viaWay.nodeRefs());
                }
            }
        }
        Map<String, OsmNodeClassification> classification = OsmNodeClassifier.classifyIntrinsic(
                importResult, acceptedWayIds, stopNodes, viaNodes,
                false, config.sharpBendAngleDegrees(), config.explicitOsmNodeIdsToKeep(),
                config.preserveCrossingNodes(), config.preserveBarrierNodes());
        Set<String> routing = OsmRoutingNodeSelector.select(graph, classification, keepAllGeometryNodes);
        routing = anchorCycles(graph, routing);
        // ... emit nodes and merged links ...
    }
```

`buildSignalReady(importResult, config, options)` is the same pipeline but also extracts transit-stop nodes from `OsmMaterializedNetwork` hints, uses `options.preserveSharpBends()`/`sharpBendAngleDegrees()`/`explicitPreserveOsmNodeIds()`, and always passes `keepAllGeometryNodes=false`.

```java
    public static CollapsedTopology build(OsmImportResult importResult,
                                          OsmNetworkBuildConfig config,
                                          boolean signalReady) {
        List<OsmImportIssue> issues = new ArrayList<>(importResult.issues());
        Set<String> acceptedWayIds = new TreeSet<>();
        for (OsmWayRecord w : importResult.ways().values()) {
            if (config.resolveRule(w.tags()) != null && w.nodeRefs().size() >= 2) {
                acceptedWayIds.add(w.id());
            }
        }
        OsmSegmentGraph graph = OsmSegmentGraph.build(importResult, config);

        Set<String> stopNodes = new TreeSet<>();
        Set<String> viaNodes = new TreeSet<>();
        if (signalReady) {
            for (OsmRelationRecord rel : importResult.relations().values()) {
                String type = rel.tags().get("type");
                if (!("restriction".equals(type) || "keep_right".equals(type) || "keep_left".equals(type))) continue;
                for (OsmRelationMemberRecord m : rel.members()) {
                    if ("via".equals(m.role()) && m.type() == OsmElementType.NODE) viaNodes.add(m.ref());
                }
            }
            for (OsmNodeRecord nd : importResult.nodes().values()) {
                if (nd.tags().has("highway", "bus_stop") || nd.tags().has("highway", "tram_stop")
                        || nd.tags().get("public_transport") != null || nd.tags().get("railway") != null) {
                    stopNodes.add(nd.id());
                }
            }
        }
        // Signal-ready simplification ALWAYS contracts geometry nodes (that is its purpose);
        // only the plain build() path honors MATERIALIZE_GEOMETRY_NODES as keep-all.
        boolean keepAll = !signalReady
                && config.geometryMode() == OsmGeometryMode.MATERIALIZE_GEOMETRY_NODES;
        Map<String, OsmNodeClassification> classification = OsmNodeClassifier.classifyIntrinsic(
                importResult, acceptedWayIds, stopNodes, viaNodes,
                false, config.sharpBendAngleDegrees(), config.explicitOsmNodeIdsToKeep(),
                config.preserveCrossingNodes(), config.preserveBarrierNodes());
        Set<String> routing = OsmRoutingNodeSelector.select(graph, classification, keepAll);
        routing = anchorCycles(graph, routing); // ensure every component has a routing node

        // ... emit nodes and merged links (see steps below) ...
    }
```

For each routing node in sorted order: `network.createNode(OsmGeneratedIds.nodeId(id), coord)`. Then walk: for each routing node `start`, for each incident segment not yet consumed, follow the chain of non-routing nodes until the next routing node `end`, accumulating segments (respecting direction) and geometry points, then emit links for each allowed direction using `OsmGeneratedIds.simplifiedLinkId(firstWayId, forward, start, end)`, mark `osm:simplified`, `osm:segmentCount`, copy lane tags, add `OsmCollapsedLink`, index under each `sourceOsmWayIds()`, add geometry. Use a per-segment visited set keyed by `wayId|segmentIndex` so each direction is emitted once. `anchorCycles` picks `Collections.min(component)` when a component's routing set is empty.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -o -B test -Dtest=OsmTopologyBuilderTest`
Expected: PASS (4 tests, including the invariance oracle).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(osm): cross-way topology contraction engine + multi-way collapsed link"
```

---

### Task 4: Wire `build()` to contract

**Files:**
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmMatsimNetworkBuilder.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmMatsimNetworkBuilderTest.java`

**Interfaces:**
- Consumes: `OsmTopologyBuilder`, `CollapsedTopology`.
- Produces: `build()` returns a contracted `Network` for `PRESERVE_AS_LINK_GEOMETRY` / `ROUTING_NODES_ONLY`; unchanged one-node-per-OSM-node for `MATERIALIZE_GEOMETRY_NODES`. `linkRefsByLinkId` is derived from the collapsed links and is keyed by the actual network link id (`value.linkId() == key`).

- [ ] **Step 1: Write the failing test** (append to `OsmMatsimNetworkBuilderTest`)

```java
    @Test
    void preservesGeometryModeContractsTopology() {
        // 5 collinear nodes, one way; default (PRESERVE) must collapse to 2 nodes.
        var ns = new java.util.TreeMap<String, com.citymodeler.matsim.models.osm.model.OsmNodeRecord>();
        for (int i = 0; i < 5; i++) {
            ns.put("N" + i, new com.citymodeler.matsim.models.osm.model.OsmNodeRecord(
                    "N" + i, i, 0, new com.citymodeler.matsim.models.api.Coord(i, 0),
                    com.citymodeler.matsim.models.osm.OsmTagSet.empty()));
        }
        var ws = new java.util.TreeMap<String, com.citymodeler.matsim.models.osm.model.OsmWayRecord>();
        ws.put("10", new com.citymodeler.matsim.models.osm.model.OsmWayRecord("10",
                java.util.List.of("N0", "N1", "N2", "N3", "N4"),
                com.citymodeler.matsim.models.osm.OsmTagSet.of(java.util.Map.of("highway", "residential"))));
        var r = new com.citymodeler.matsim.models.osm.OsmImportResult(ns, ws,
                new java.util.TreeMap<>(), java.util.List.of(),
                com.citymodeler.matsim.models.osm.OsmProvenance.defaultFor("f.osm", "EPSG:3857"));

        var built = new com.citymodeler.matsim.models.osm.network.OsmMatsimNetworkBuilder()
                .build(r, com.citymodeler.matsim.models.osm.network.OsmNetworkBuildConfig.defaultConfig());

        assertEquals(2, built.cleanedNetwork().getNodes().size());
        assertEquals(2, built.cleanedNetwork().getLinks().size()); // fwd + rev
        assertEquals("3", built.cleanedNetwork().getLinks().values().iterator().next()
                .getAttributes().getAttribute("osm:segmentCount"));
    }
```

Note: if `OsmMatsimNetworkBuilderTest` uses a differently named helper/imports, keep the assertions and adapt imports.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -B test -Dtest=OsmMatsimNetworkBuilderTest#preservesGeometryModeContractsTopology`
Expected: FAIL — 5 nodes / 8 links.

- [ ] **Step 3: Implement**

Rewrite `build()` to delegate to `OsmTopologyBuilder.build(importResult, config, keepAll)` where `keepAll = config.geometryMode() == MATERIALIZE_GEOMETRY_NODES`, and assemble `OsmNetworkBuildResult` from it (network, issues, `linkRefsByLinkId` flattened from `collapsedLinksByLinkId().values()`, `linkIdsByOsmWayId`). Keep `MATERIALIZE_GEOMETRY_NODES` producing the all-node network (engine handles it via `keepAll`). Keep stop/lane/intersection hint extraction and the `OsmGeometryStore` populated from `CollapsedTopology.geometry()`. Delete `createSegments` and the accepted-node loop.

- [ ] **Step 4: Run all OSM network tests**

Run: `mvn -o -B test -Dtest='OsmMatsimNetworkBuilderTest,OsmNetworkEdgeCasesTest,OsmGridIntegrationTest'`
Expected: PASS. Update any test that asserted one-node-per-OSM-node counts under a non-MATERIALIZE config to the collapsed counts (or switch that test to `materializeGeometryConfig()` if it is specifically testing materialization).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(osm): build() contracts topology for PRESERVE/ROUTING_NODES_ONLY modes"
```

---

### Task 5: Wire `simplify()` / `buildSignalReady()` to the shared engine

**Files:**
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmSignalAwareSimplifier.java`
- Delete: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkSimplifier.java` (its routing-node logic is superseded; remove references)
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmSignalAwareSimplifierTest.java`, `OsmSignalReadinessIntegrationTest.java`

**Interfaces:**
- Consumes: `OsmTopologyBuilder.buildSignalReady(importResult, config, options)`, `CollapsedTopology`.
- Produces: `simplify()` consumes the collapsed network/geometry/collapsed-links/classification from the engine (no per-way `processChain`), then builds junctions/restrictions/report exactly as today. Removes single-way limitation.

- [ ] **Step 1: Write the failing test** (append to `OsmSignalAwareSimplifierTest`)

```java
    /** Cross-way: a degree-2 way split must not survive signal-ready simplification. */
    @Test
    void splitsAcrossWaysCollapseInSignalReady() {
        var ns = new java.util.TreeMap<String, com.citymodeler.matsim.models.osm.model.OsmNodeRecord>();
        ns.put("A", new com.citymodeler.matsim.models.osm.model.OsmNodeRecord(
                "A", 0, 0, new com.citymodeler.matsim.models.api.Coord(0, 0), com.citymodeler.matsim.models.osm.OsmTagSet.empty()));
        ns.put("B", new com.citymodeler.matsim.models.osm.model.OsmNodeRecord(
                "B", 100, 0, new com.citymodeler.matsim.models.api.Coord(100, 0), com.citymodeler.matsim.models.osm.OsmTagSet.empty()));
        ns.put("C", new com.citymodeler.matsim.models.osm.model.OsmNodeRecord(
                "C", 200, 0, new com.citymodeler.matsim.models.api.Coord(200, 0), com.citymodeler.matsim.models.osm.OsmTagSet.empty()));
        var ws = new java.util.TreeMap<String, com.citymodeler.matsim.models.osm.model.OsmWayRecord>();
        ws.put("10", new com.citymodeler.matsim.models.osm.model.OsmWayRecord("10",
                java.util.List.of("A", "B"), com.citymodeler.matsim.models.osm.OsmTagSet.of(java.util.Map.of("highway", "residential"))));
        ws.put("11", new com.citymodeler.matsim.models.osm.model.OsmWayRecord("11",
                java.util.List.of("B", "C"), com.citymodeler.matsim.models.osm.OsmTagSet.of(java.util.Map.of("highway", "residential"))));
        var r = new com.citymodeler.matsim.models.osm.OsmImportResult(ns, ws,
                new java.util.TreeMap<>(), java.util.List.of(),
                com.citymodeler.matsim.models.osm.OsmProvenance.defaultFor("f.osm", "EPSG:3857"));

        var s = new com.citymodeler.matsim.models.osm.network.OsmMatsimNetworkBuilder()
                .buildSignalReady(r, com.citymodeler.matsim.models.osm.network.OsmNetworkBuildConfig.defaultConfig());

        assertEquals(2, s.network().getNodes().size());
        assertFalse(s.network().getNodes().containsKey(
                com.citymodeler.matsim.models.api.Id.create("osm_node_B",
                        com.citymodeler.matsim.models.network.Node.class)));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -B test -Dtest=OsmSignalAwareSimplifierTest#splitsAcrossWaysCollapseInSignalReady`
Expected: FAIL — node `osm_node_B` still present.

- [ ] **Step 3: Implement**

Replace the network-building portion of `simplify()` (the accepted-way loop + `processChain` + node loop) with a call to `OsmTopologyBuilder.buildSignalReady(importResult, config, options)` (use the passed `importResult`). Consume `CollapsedTopology.network()`, `geometry()`, `collapsedLinksByLinkId()`, `linkIdsByOsmWayId()`, `classification()`. Delete `processChain`, `copyLaneTags` duplication is retained but applied inside the engine, so keep a single copy in the engine. Then proceed to restriction reading, junction building, and report using the engine's network/classification. Update the classifier call to `classifyIntrinsic` (8-arg at this task; the 9th `preserveBarrierNodes` arrives in Task 6).

- [ ] **Step 4: Run the signal-ready tests**

Run: `mvn -o -B test -Dtest='OsmSignalAwareSimplifierTest,OsmSignalReadinessIntegrationTest,JunctionSignalDescriptorTest,OsmCityFixtureIntegrationTest'`
Expected: PASS. Update `OsmSignalAwareSimplifierTest` expectations that assumed single-way collapse for multi-way inputs; keep single-way cases identical.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(osm): signal-ready simplification uses shared contraction engine"
```

---

### Task 6: Metadata-node policy (crossings/barriers do not always split routing)

**Files:**
- Modify: `OsmNetworkBuildConfig.java` (already carries `preserveBarrierNodes`, `preserveCrossingNodes` from Task 2)
- Modify: `OsmNodeClassifier.java` — `classifyIntrinsic(...)` gains the 9th `boolean preserveBarrierNodes`; emit `BARRIER` only when true (default keeps barriers). No barrier logic in the selector.
- Test: `OsmRoutingNodeSelectorTest.java` (append)

**Interfaces:**
- Consumes: the config flags.
- Produces: `classifyIntrinsic(...)` takes both flags — it emits `CROSSING` only when `preserveCrossingNodes` and `BARRIER` only when `preserveBarrierNodes` (the barrier flag is the 9th, added here). `select(...)` consumes the already-filtered classification; no barrier logic lives in the selector.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void crossingDoesNotSplitRoutingByDefault() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100, "highway", "crossing"));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential"));
        ways.put("11", way("11", List.of("B", "C"), "highway", "residential"));
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);
        assertTrue(OsmRoutingNodeSelector.contractible(g, "B"));
    }

    @Test
    void barrierSplitsRoutingByDefault() {
        Map<String, OsmNodeRecord> nodes = new java.util.TreeMap<>();
        nodes.put("A", node("A", 0));
        nodes.put("B", node("B", 100, "barrier", "gate"));
        nodes.put("C", node("C", 200));
        Map<String, OsmWayRecord> ways = new java.util.TreeMap<>();
        ways.put("10", way("10", List.of("A", "B"), "highway", "residential"));
        ways.put("11", way("11", List.of("B", "C"), "highway", "residential"));
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmSegmentGraph g = OsmSegmentGraph.build(result(nodes, ways), cfg);
        Map<String, OsmNodeClassification> cls = OsmNodeClassifier.classifyIntrinsic(
                result(nodes, ways), Set.of("10", "11"), Set.of(), Set.of(), false, 30.0, Set.of(), false);
        assertFalse(OsmRoutingNodeSelector.contractible(g, "B"));
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -o -B test -Dtest=OsmRoutingNodeSelectorTest`
Expected: the barrier test currently passes (SEMANTIC_NODE_TAG kept it); the crossing test may pass or fail depending on Task 2. If both already pass, add the assertion that `OsmNodeClassifier` did NOT emit `CROSSING` when `preserveCrossingNodes=false`, and that it DOES when `true`.

- [ ] **Step 3: Implement** any gap (ensure `hasSemanticNodeTag` no longer returns true for a bare `crossing`, and `isBarrier` marks `BARRIER`).

- [ ] **Step 4: Run + Commit**

Run: `mvn -o -B test -Dtest=OsmRoutingNodeSelectorTest` → PASS.

```bash
git add -A
git commit -m "feat(osm): crossings are metadata by default; barriers still split routing"
```

---

### Task 7: Optional isolated/small-component cleanup

**Files:**
- Modify: `OsmNetworkBuildConfig.java` (add `boolean cleanupIsolatedComponents`, default `false`)
- Modify: `OsmTopologyBuilder.java` (when enabled, call `OsmNetworkCleaner.clean(network, false)` before returning; append its issues)
- Test: `OsmTopologyBuilderTest.java` (append)

**Interfaces:**
- Consumes: `OsmNetworkCleaner.clean(Network, boolean)` → `CleanResult(removedLinks, removedNodes, issues)`.
- Produces: with `cleanupIsolatedComponents=true`, components consisting only of `car`-only links with no `bus/pt/rail/tram/subway` are removed.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void cleanupRemovesIsolatedNonTransitComponentWhenEnabled() {
        // Component 1: connected road. Component 2: isolated stub far away.
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0)); ns.put("B", n("B", 100));
        ns.put("X", n("X", 100000)); ns.put("Y", n("Y", 100100));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", w("10", List.of("A", "B")));
        ws.put("11", w("11", List.of("X", "Y")));
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.defaultConfigWithCleanup();
        CollapsedTopology t = OsmTopologyBuilder.build(res(ns, ws), cfg, false);
        assertFalse(t.network().getNodes().keySet().stream().anyMatch(id -> id.toString().contains("X")));
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -o -B test -Dtest=OsmTopologyBuilderTest#cleanupRemovesIsolatedNonTransitComponentWhenEnabled`
Expected: FAIL — `defaultConfigWithCleanup()` missing.

- [ ] **Step 3: Implement** `defaultConfigWithCleanup()` factory (new config with `cleanupIsolatedComponents=true`) and the cleanup call.

> Note: `OsmNetworkCleaner` removes links/nodes whose whole component lacks transit modes. A two-node all-`car` stub is a component; both components here are all-`car`. The cleaner's current contract keys on transit mode presence, so both would be removed. If so, adjust the test to use a `bus`-tagged component that must survive, matching the cleaner's actual contract (`removeDisconnectedNonTransit`). Keep the test aligned to real behavior.

- [ ] **Step 4: Run + Commit**

Run: `mvn -o -B test -Dtest=OsmTopologyBuilderTest` → PASS.

```bash
git add -A
git commit -m "feat(osm): optional isolated non-transit component cleanup"
```

---

### Task 8: Full gate + re-measure by road class

**Files:**
- Modify: `docs/pt2matsim-blackbox-comparison.md` (add post-fix measurements)
- No production code (measurement only)

- [ ] **Step 1: Full suite**

Run: `mvn -o clean verify`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 2: Re-run the black-box comparison on Luxembourg (same OSM+GTFS)**

Using the persistent workspace `/home/ashraf/git/matsim-compare` (outside the repo so `mvn clean` cannot wipe it): rebuild our bundle, then compare node/link counts and per-road-class link counts against the pt2MATSim artifacts, and record the remaining delta. Do **not** change `highway=service` scope in this task.

- [ ] **Step 3: Record findings**

Append a "Post-contraction" section to `docs/pt2matsim-blackbox-comparison.md` with the new counts and the per-class breakdown, and state the remaining differences as policy choices (not defects).

- [ ] **Step 4: Commit**

```bash
git add docs/pt2matsim-blackbox-comparison.md
git commit -m "docs: post-contraction network-size comparison"
```

---

## Self-Review

**Spec coverage:** routing-node rules (§Geometry Preservation) → Tasks 2/3; `geometryMode` topology semantics → Tasks 3/4; cross-way contraction → Task 3; multi-way collapsed link → Task 3; full polyline preservation → Task 3 (`geometry`); metadata nodes not splitting routing → Task 6; optional cleanup → Task 7; invariance oracle → Task 3/Task 5. Gap: "keep barrier nodes on converted ways" is Task 6; "via-way restriction chains retain all nodes" is covered because via nodes are intrinsic reasons (Task 2) — via-way chains' interior nodes are degree-2 and will contract, so restrict via-way handling must treat the chain's interior as routing nodes: **added requirement** — `OsmTopologyBuilder` must keep all nodes of a via-way restriction chain (not just the via node). Implement by adding chain interior nodes to the intrinsic keep set in Task 2's extraction (collect every node of every way whose role is `via` in a restriction relation).

**Placeholder scan:** no "TBD"/"handle edge cases"/"similar to Task N"; every code step shows concrete code. Task 3's chain-walk is described with a complete method contract and the invariants it must satisfy; the implementer writes the loop against the stated rule (emit one link per maximal routing-node-to-routing-node chain per allowed direction, one `OsmCollapsedLink`, indexed under every source way, geometry concatenated).

**Type consistency:** `OsmCollapsedLink` is the 5-arg record `(linkId, forward, fromOsmNode, toOsmNode, sourceSegments)` with derived `sourceOsmWayIds()`/`firstOsmWayId()` consistently across Tasks 3–5; the classifier entry point is `OsmNodeClassifier.classifyIntrinsic(...)` — intrinsic-only, 8-arg in Tasks 2/5 and gaining a 9th `preserveBarrierNodes` boolean in Task 6 (the legacy `classify(...)` was deleted); `OsmNetworkBuildResult.linkRefsByLinkId` is keyed by the **actual network link id** (`value.linkId() == key`); `OsmRoutingNodeSelector.select/contractible` names are stable; `simplifiedLinkId(firstWayId, forward, from, to)` matches the existing `OsmGeneratedIds` signature.

**Known churn:** `OsmNodeClassifierTest`, `OsmMatsimNetworkBuilderTest`, `OsmNetworkEdgeCasesTest`, `OsmGridIntegrationTest`, `OsmSignalAwareSimplifierTest`, `OsmBoundaryFilterTest` may assert uncollapsed counts; Tasks 4/5 call these out. `OsmNetworkSimplifier` is deleted in Task 5; confirm no remaining references.
