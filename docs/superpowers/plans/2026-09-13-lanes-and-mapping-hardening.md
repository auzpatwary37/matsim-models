# Lanes and Mapping Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a schema-correct `laneDefinitions.xml` from OSM lane tags and close the phase-2 mapping verification gap.

**Architecture:** OSM lane tags are resolved per travel direction into a lane count + `turn:lanes` cells, decomposed into the existing `Lanes`/`LanesToLinkAssignment`/`Lane` model with provenance attributes, then written in the published `laneDefinitions_v2.0` shape. Separately, seven missing mapping verification tests are added and whatever they surface is fixed, with a GeoJSON spot-check export and numeric stop→link assertions.

**Tech Stack:** Java 17, Maven (surefire, checkstyle, SpotBugs), JUnit 5, JDK DOM + XSD validation. No MATSim runtime dependency (enforcer + `NoMatsimRuntimeImportsTest`).

**Spec:** `docs/superpowers/specs/2026-09-13-lanes-and-mapping-design.md`

## Global Constraints

- No `import org.matsim.*` in `src/main/java`; the only permitted `org.matsim.*` string literal in production is the existing allowlist in `XmlSupport` (`org.matsim.core.network.turnRestrictions.DisallowedNextLinks` + historic alias).
- Clean-room: only this repo's specs, OSM wiki semantics, GTFS spec, and published MATSim XSDs. pt2MATSim is a black-box oracle only.
- All output deterministic: traverse in sorted order; lane id = `<linkId>_l<index>` (left→right in direction of travel).
- Never fabricate a lane count, movement, or alignment. Unknown → schema-legal fallback + provenance attribute + `OsmImportIssue`.
- Full gate before completion: `mvn -o -B clean verify` (tests, checkstyle, SpotBugs).
- `laneDefinitions` child order is fixed by the published XSD: `leadsTo, representedLanes, capacity, startsAt, alignment, attributes`. `leadsTo` and `alignment` are mandatory.
- Confidence vocabulary is exactly: `present`, `wiki-default-even-split`, `undetermined-split`, `turn-lanes-authoritative`, `absent`, `none-observed`, `unsupported`, `partial`.

---

### Task 1: Directional lane-count resolution

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/LaneConfidence.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneCount.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmDirectionalLaneResolver.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmDirectionalLaneResolverTest.java`

**Interfaces:**
- Consumes: `OsmTagSet` (`get(String)`, `has(String)`).
- Produces:
  - `LaneConfidence.PRESENT|EVEN_SPLIT|UNDETERMINED_SPLIT|TURN_LANES_AUTHORITATIVE|ABSENT|NONE_OBSERVED|UNSUPPORTED|PARTIAL` (String constants).
  - `record OsmLaneCount(int lanes, String confidence, Double undeterminedTotal, List<String> issueCodes)`.
  - `OsmDirectionalLaneResolver.resolve(OsmTagSet tags, boolean forward, boolean oneway) -> OsmLaneCount`.

- [ ] **Step 1: Write the failing test**

```java
package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.osm.OsmTagSet;

class OsmDirectionalLaneResolverTest {

    private final OsmDirectionalLaneResolver resolver = new OsmDirectionalLaneResolver();

    private static OsmTagSet tags(Map<String, String> t) {
        return OsmTagSet.of(t);
    }

    @Test
    void explicitDirectionalTagIsAuthoritative() {
        OsmTagSet t = tags(Map.of("lanes", "2", "lanes:forward", "3", "lanes:backward", "1"));
        assertEquals(3, resolver.resolve(t, true, false).lanes());
        assertEquals(1, resolver.resolve(t, false, false).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(t, true, false).confidence());
    }

    @Test
    void onewayUsesTotalForTravelledDirection() {
        OsmTagSet t = tags(Map.of("highway", "primary", "oneway", "yes", "lanes", "3"));
        assertEquals(3, resolver.resolve(t, true, true).lanes());
        assertEquals(LaneConfidence.PRESENT, resolver.resolve(t, true, true).confidence());
    }

    @Test
    void bidirectionalEvenTotalSplitsEvenly() {
        assertEquals(1, resolver.resolve(tags(Map.of("lanes", "2")), true, false).lanes());
        assertEquals(2, resolver.resolve(tags(Map.of("lanes", "4")), true, false).lanes());
        assertEquals(2, resolver.resolve(tags(Map.of("lanes", "4")), false, false).lanes());
        assertEquals(LaneConfidence.EVEN_SPLIT,
                resolver.resolve(tags(Map.of("lanes", "4")), true, false).confidence());
    }

    @Test
    void bidirectionalOddTotalIsUndeterminedNotFabricated() {
        OsmLaneCount c = resolver.resolve(tags(Map.of("lanes", "3")), true, false);
        assertEquals(1, c.lanes(), "undetermined split keeps a single undivided lane");
        assertEquals(LaneConfidence.UNDETERMINED_SPLIT, c.confidence());
        assertEquals(3.0, c.undeterminedTotal());
        assertTrue(c.issueCodes().contains("undetermined-lane-split"));
    }

    @Test
    void bothWaysIsExcludedFromTheSplitAndCountedForBothDirections() {
        // 5 total - 1 center = 4 directional -> 2 per direction, plus the shared lane.
        OsmLaneCount fwd = resolver.resolve(
                tags(Map.of("lanes", "5", "lanes:both_ways", "1")), true, false);
        assertEquals(3, fwd.lanes(), "2 through + 1 both_ways");
        assertEquals(2, fwd.lanes() - 1);
        assertEquals(LaneConfidence.EVEN_SPLIT, fwd.confidence());
    }

    @Test
    void malformedCountsAreIgnoredWithIssue() {
        for (String bad : List.of("0", "-1", "1.5", "none", " ")) {
            OsmLaneCount c = resolver.resolve(tags(Map.of("lanes", bad)), true, false);
            assertEquals(LaneConfidence.ABSENT, c.confidence());
            assertEquals(1, c.lanes());
        }
        OsmLaneCount c = resolver.resolve(tags(Map.of("lanes", "0")), true, false);
        assertTrue(c.issueCodes().contains("malformed-lane-count"));
        assertNull(c.undeterminedTotal());
    }

    @Test
    void noLaneTagsGivesOneAbsentLane() {
        OsmLaneCount c = resolver.resolve(tags(Map.of("highway", "residential")), true, false);
        assertEquals(1, c.lanes());
        assertEquals(LaneConfidence.ABSENT, c.confidence());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -B -Dtest=OsmDirectionalLaneResolverTest test`
Expected: FAIL — classes `LaneConfidence`/`OsmLaneCount`/`OsmDirectionalLaneResolver` do not exist.

- [ ] **Step 3: Write the implementation**

`LaneConfidence.java`:

```java
package com.citymodeler.matsim.models.osm.network;

/** Provenance vocabulary for a lane count / lane movement. */
public final class LaneConfidence {
    public static final String PRESENT = "present";
    public static final String EVEN_SPLIT = "wiki-default-even-split";
    public static final String UNDETERMINED_SPLIT = "undetermined-split";
    public static final String TURN_LANES_AUTHORITATIVE = "turn-lanes-authoritative";
    public static final String ABSENT = "absent";
    public static final String NONE_OBSERVED = "none-observed";
    public static final String UNSUPPORTED = "unsupported";
    public static final String PARTIAL = "partial";

    private LaneConfidence() {
    }
}
```

`OsmLaneCount.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.List;

/** Resolved lane count for one travel direction, with provenance. */
public record OsmLaneCount(int lanes, String confidence, Double undeterminedTotal, List<String> issueCodes) {
    public OsmLaneCount {
        if (lanes < 1) {
            throw new IllegalArgumentException("lanes must be >= 1");
        }
        issueCodes = List.copyOf(issueCodes);
    }
}
```

`OsmDirectionalLaneResolver.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;

import com.citymodeler.matsim.models.osm.OsmTagSet;

/**
 * Resolves the lane count that applies to one travel direction of an OSM way.
 * {@code lanes=*} is the total across BOTH directions; only an explicit directional tag or the
 * oneway assumption gives an unambiguous per-direction count. When a bidirectional way carries an
 * even total the OSM-documented even split is applied; an odd total is left undetermined (one
 * undivided lane + issue) rather than guessed.
 */
public final class OsmDirectionalLaneResolver {

    public OsmLaneCount resolve(OsmTagSet tags, boolean forward, boolean oneway) {
        List<String> issues = new ArrayList<>();
        String directionalKey = forward ? "lanes:forward" : "lanes:backward";
        Double directional = parse(tags.get(directionalKey), directionalKey, issues);
        Double total = parse(tags.get("lanes"), "lanes", issues);
        Double bothWays = parse(tags.get("lanes:both_ways"), "lanes:both_ways", issues);
        double both = bothWays != null ? bothWays : 0.0;

        if (oneway) {
            if (directional != null) {
                return new OsmLaneCount((int) Math.round(directional + both), LaneConfidence.PRESENT,
                        null, issues);
            }
            if (total != null) {
                double lanes = total + both;
                return new OsmLaneCount((int) Math.round(lanes), LaneConfidence.PRESENT, null, issues);
            }
            return new OsmLaneCount(1, LaneConfidence.ABSENT, null, issues);
        }

        Double fwd = parse(tags.get("lanes:forward"), "lanes:forward", issues);
        Double bwd = parse(tags.get("lanes:backward"), "lanes:backward", issues);
        if (fwd != null && bwd != null) {
            double lanes = (forward ? fwd : bwd) + both;
            return new OsmLaneCount((int) Math.round(lanes), LaneConfidence.PRESENT, null, issues);
        }

        if (total != null) {
            double directionalTotal = total - both;
            if (directionalTotal < 1.0) {
                issues.add("undetermined-lane-split");
                return new OsmLaneCount(1, LaneConfidence.UNDETERMINED_SPLIT, total, issues);
            }
            double perDirection = directionalTotal / 2.0;
            if (Math.abs(perDirection - Math.rint(perDirection)) > 1e-9) {
                issues.add("undetermined-lane-split");
                return new OsmLaneCount(1, LaneConfidence.UNDETERMINED_SPLIT, directionalTotal, issues);
            }
            int lanes = (int) Math.round(perDirection + both);
            return new OsmLaneCount(lanes, LaneConfidence.EVEN_SPLIT, null, issues);
        }

        return new OsmLaneCount(1, LaneConfidence.ABSENT, null, issues);
    }

    private static Double parse(String value, String key, List<String> issues) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double d = Double.parseDouble(value.trim());
            if (d < 1.0 || Math.abs(d - Math.rint(d)) > 1e-9) {
                issues.add("malformed-lane-count");
                return null;
            }
            return d;
        } catch (NumberFormatException e) {
            issues.add("malformed-lane-count");
            return null;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -o -B -Dtest=OsmDirectionalLaneResolverTest test`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/osm/network/LaneConfidence.java \
        src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneCount.java \
        src/main/java/com/citymodeler/matsim/models/osm/network/OsmDirectionalLaneResolver.java \
        src/test/java/com/citymodeler/matsim/models/osm/network/OsmDirectionalLaneResolverTest.java
git commit -m "feat(lanes): directional lane-count resolution with provenance"
```

---

### Task 2: `turn:lanes` token parsing

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/LaneTurnClass.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/LaneMerge.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmTurnLaneCell.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmTurnLaneParser.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmTurnLaneParserTest.java`

**Interfaces:**
- Produces:
  - `enum LaneTurnClass { SHARP_LEFT, LEFT, SLIGHT_LEFT, THROUGH, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, REVERSE, UNKNOWN }`.
  - `enum LaneMerge { NONE, LEFT, RIGHT }`.
  - `record OsmTurnLaneCell(String raw, List<LaneTurnClass> indications, LaneMerge merge, boolean empty, List<String> unsupportedTokens)`.
  - `OsmTurnLaneParser.parse(String value) -> List<OsmTurnLaneCell>` (empty list for null/blank).

- [ ] **Step 1: Write the failing test**

```java
package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class OsmTurnLaneParserTest {

    private final OsmTurnLaneParser parser = new OsmTurnLaneParser();

    @Test
    void splitsCellsAndTokensInOrder() {
        List<OsmTurnLaneCell> cells = parser.parse("left|through;right|none");
        assertEquals(3, cells.size());
        assertEquals(List.of(LaneTurnClass.LEFT), cells.get(0).indications());
        assertEquals(List.of(LaneTurnClass.THROUGH, LaneTurnClass.RIGHT), cells.get(1).indications());
        assertTrue(cells.get(2).indications().isEmpty(), "'none' is not a movement class here");
        assertFalse(cells.get(2).empty());
    }

    @Test
    void recognisesEveryMovementToken() {
        assertEquals(LaneTurnClass.SHARP_LEFT, parser.parse("sharp_left").get(0).indications().get(0));
        assertEquals(LaneTurnClass.SLIGHT_LEFT, parser.parse("slight_left").get(0).indications().get(0));
        assertEquals(LaneTurnClass.SLIGHT_RIGHT, parser.parse("slight_right").get(0).indications().get(0));
        assertEquals(LaneTurnClass.SHARP_RIGHT, parser.parse("sharp_right").get(0).indications().get(0));
        assertEquals(LaneTurnClass.REVERSE, parser.parse("reverse").get(0).indications().get(0));
    }

    @Test
    void mergeTokensCarryAMergeDirectionNotAMovement() {
        OsmTurnLaneCell c = parser.parse("merge_to_left").get(0);
        assertEquals(LaneMerge.LEFT, c.merge());
        assertTrue(c.indications().isEmpty());
        assertEquals(LaneMerge.RIGHT, parser.parse("merge_to_right").get(0).merge());
    }

    @Test
    void emptyCellIsMarkedEmpty() {
        List<OsmTurnLaneCell> cells = parser.parse("|through");
        assertTrue(cells.get(0).empty());
        assertTrue(cells.get(0).indications().isEmpty());
    }

    @Test
    void unknownTokenIsPreservedNotGuessed() {
        OsmTurnLaneCell c = parser.parse("left_turn").get(0);
        assertTrue(c.indications().isEmpty());
        assertEquals(List.of("left_turn"), c.unsupportedTokens());
        assertEquals("left_turn", c.raw());
    }

    @Test
    void blankInputYieldsNoCells() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("  ").isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -B -Dtest=OsmTurnLaneParserTest test`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Write the implementation**

`LaneTurnClass.java`:

```java
package com.citymodeler.matsim.models.osm.network;

/** Geometric/turn-lane class of a movement. */
public enum LaneTurnClass {
    SHARP_LEFT,
    LEFT,
    SLIGHT_LEFT,
    THROUGH,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    REVERSE,
    UNKNOWN
}
```

`LaneMerge.java`:

```java
package com.citymodeler.matsim.models.osm.network;

/** A lane that ends by merging, per {@code merge_to_left}/{@code merge_to_right}. */
public enum LaneMerge {
    NONE,
    LEFT,
    RIGHT
}
```

`OsmTurnLaneCell.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Objects;

/** One {@code turn:lanes} cell: its raw text, parsed movements, merge direction and unknowns. */
public record OsmTurnLaneCell(
        String raw,
        List<LaneTurnClass> indications,
        LaneMerge merge,
        boolean empty,
        List<String> unsupportedTokens) {

    public OsmTurnLaneCell {
        raw = Objects.requireNonNull(raw, "raw");
        indications = List.copyOf(indications);
        merge = Objects.requireNonNull(merge, "merge");
        unsupportedTokens = List.copyOf(unsupportedTokens);
    }
}
```

`OsmTurnLaneParser.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses a {@code turn:lanes} value into ordered left→right cells in the direction of travel. */
public final class OsmTurnLaneParser {

    public List<OsmTurnLaneCell> parse(String value) {
        List<OsmTurnLaneCell> cells = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return cells;
        }
        for (String cellText : value.toLowerCase(Locale.ROOT).split("\\|", -1)) {
            cells.add(parseCell(cellText.trim()));
        }
        return cells;
    }

    private OsmTurnLaneCell parseCell(String cellText) {
        List<LaneTurnClass> indications = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        LaneMerge merge = LaneMerge.NONE;
        if (cellText.isEmpty()) {
            return new OsmTurnLaneCell("", indications, merge, true, unsupported);
        }
        for (String token : cellText.split(";")) {
            String t = token.trim();
            switch (t) {
                case "left" -> indications.add(LaneTurnClass.LEFT);
                case "slight_left" -> indications.add(LaneTurnClass.SLIGHT_LEFT);
                case "sharp_left" -> indications.add(LaneTurnClass.SHARP_LEFT);
                case "through" -> indications.add(LaneTurnClass.THROUGH);
                case "right" -> indications.add(LaneTurnClass.RIGHT);
                case "slight_right" -> indications.add(LaneTurnClass.SLIGHT_RIGHT);
                case "sharp_right" -> indications.add(LaneTurnClass.SHARP_RIGHT);
                case "reverse" -> indications.add(LaneTurnClass.REVERSE);
                case "merge_to_left" -> merge = LaneMerge.LEFT;
                case "merge_to_right" -> merge = LaneMerge.RIGHT;
                case "none" -> { /* no marked indication; not a movement class */ }
                default -> unsupported.add(t);
            }
        }
        return new OsmTurnLaneCell(cellText, indications, merge, false, unsupported);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -o -B -Dtest=OsmTurnLaneParserTest test`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/osm/network/LaneTurnClass.java \
        src/main/java/com/citymodeler/matsim/models/osm/network/LaneMerge.java \
        src/main/java/com/citymodeler/matsim/models/osm/network/OsmTurnLaneCell.java \
        src/main/java/com/citymodeler/matsim/models/osm/network/OsmTurnLaneParser.java \
        src/test/java/com/citymodeler/matsim/models/osm/network/OsmTurnLaneParserTest.java
git commit -m "feat(lanes): deterministic turn:lanes token parser"
```

---

### Task 3: Lane decomposer (counts + cells → `LanesToLinkAssignment`)

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/MovementTurnClassifier.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/LaneDecomposition.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneDecomposer.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmLaneDecomposerTest.java`

**Interfaces:**
- Consumes: Task 1 `OsmLaneCount`, `LaneConfidence`; Task 2 `OsmTurnLaneCell`, `LaneTurnClass`, `LaneMerge`; `LanesToLinkAssignment`/`Lane` from `models.lanes`.
- Produces:
  - `@FunctionalInterface MovementTurnClassifier { LaneTurnClass classify(String inLinkId, String outLinkId); }`
  - `record LaneDecomposition(LanesToLinkAssignment assignment, List<OsmImportIssue> issues)`
  - `OsmLaneDecomposer.decompose(String linkId, OsmLaneCount count, List<OsmTurnLaneCell> cells, List<String> outgoingLinkIds, MovementTurnClassifier classifier, double capacityPerLane) -> LaneDecomposition`
- Attribute keys written on each `Lane`: `osm:lane.confidence`, `osm:lane.rawToken`, `osm:lane.merge`, `osm:lane.count`, `osm:lane.capacity.shared`, `osm:lanes.total`.
- Issue codes: `lane-count-mismatch`, `unsupported-turn-token`, `empty-turn-cell`.

- [ ] **Step 1: Write the failing test**

```java
package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;

class OsmLaneDecomposerTest {

    private final OsmLaneDecomposer decomposer = new OsmLaneDecomposer();
    private final OsmTurnLaneParser parser = new OsmTurnLaneParser();

    /** Junction: inLink l_in; outgoing THROUGH=l_t, LEFT=l_l, RIGHT=l_r. */
    private final MovementTurnClassifier junction = (in, out) -> switch (out) {
        case "l_t" -> LaneTurnClass.THROUGH;
        case "l_l" -> LaneTurnClass.LEFT;
        case "l_r" -> LaneTurnClass.RIGHT;
        default -> LaneTurnClass.UNKNOWN;
    };

    private Lane lane(LaneDecomposition d, int index) {
        return d.assignment().getLanes().get(Id.create("l_in_l" + index, Lane.class));
    }

    @Test
    void leftAndThroughCellsMapToTheCorrectOutgoingLinks() {
        OsmLaneCount count = new OsmLaneCount(2, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left|through"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertEquals(2, d.assignment().getLanes().size());
        assertEquals(List.of("l_l"), lane(d, 0).getToLinkIds().stream().map(Object::toString).toList());
        assertEquals(List.of("l_t"), lane(d, 1).getToLinkIds().stream().map(Object::toString).toList());
        assertEquals(900.0, lane(d, 0).getCapacityVehiclesPerHour());
        assertEquals("false", lane(d, 0).getAttributes().getAttribute("osm:lane.capacity.shared"));
    }

    @Test
    void sharedCellServesSeveralMovementsAndIsFlaggedSharedNotDoubled() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left;through"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        Lane shared = lane(d, 0);
        assertEquals(2, shared.getToLinkIds().size());
        assertEquals(900.0, shared.getCapacityVehiclesPerHour(), "lane capacity stays physical");
        assertEquals("true", shared.getAttributes().getAttribute("osm:lane.capacity.shared"));
    }

    @Test
    void absentTurnEvidenceFallsBackToAllOutgoingWithAbsentConfidence() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.ABSENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, List.of(),
                List.of("l_t", "l_l", "l_r"), junction, 600.0);

        assertEquals(3, lane(d, 0).getToLinkIds().size(), "schema requires at least one toLink");
        assertEquals(LaneConfidence.ABSENT, lane(d, 0).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void tokenCountMismatchAdoptsTurnLanesWhenLaneCountUndetermined() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.UNDETERMINED_SPLIT, 3.0, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left|through"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertEquals(2, d.assignment().getLanes().size());
        assertTrue(d.issues().stream().anyMatch(i -> i.code().equals("lane-count-mismatch")));
        assertEquals(LaneConfidence.TURN_LANES_AUTHORITATIVE,
                lane(d, 0).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void tokenCountMismatchKeepsLaneCountWhenKnownAndAlignsPositionally() {
        OsmLaneCount count = new OsmLaneCount(3, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("left|through"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertEquals(3, d.assignment().getLanes().size());
        assertEquals(2, lane(d, 2).getToLinkIds().size(), "trailing lane has no evidence -> all outgoing");
        assertEquals(LaneConfidence.ABSENT, lane(d, 2).getAttributes().getAttribute("osm:lane.confidence"));
    }

    @Test
    void mergeCellEmitsSchemaLegalFallbackAndMergeAttribute() {
        OsmLaneCount count = new OsmLaneCount(1, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("l_in", count, parser.parse("merge_to_left"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);

        assertNotNull(lane(d, 0));
        assertEquals("left", lane(d, 0).getAttributes().getAttribute("osm:lane.merge"));
        assertTrue(lane(d, 0).getToLinkIds().size() >= 1);
    }

    @Test
    void laneIdsAreDeterministicLeftToRight() {
        OsmLaneCount count = new OsmLaneCount(2, LaneConfidence.PRESENT, null, List.of());
        LaneDecomposition d = decomposer.decompose("abc", count, parser.parse("left|right"),
                List.of("l_t", "l_l", "l_r"), junction, 900.0);
        assertTrue(d.assignment().getLanes().containsKey(Id.create("abc_l0", Lane.class)));
        assertTrue(d.assignment().getLanes().containsKey(Id.create("abc_l1", Lane.class)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -B -Dtest=OsmLaneDecomposerTest test`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Write the implementation**

`MovementTurnClassifier.java`:

```java
package com.citymodeler.matsim.models.osm.network;

/** Classifies the turn from an incoming link to an outgoing link. */
@FunctionalInterface
public interface MovementTurnClassifier {
    LaneTurnClass classify(String inLinkId, String outLinkId);
}
```

`LaneDecomposition.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.List;

import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.osm.OsmImportIssue;

/** One link's lanes plus any issues raised while decomposing them. */
public record LaneDecomposition(LanesToLinkAssignment assignment, List<OsmImportIssue> issues) {
    public LaneDecomposition {
        issues = List.copyOf(issues);
    }
}
```

`OsmLaneDecomposer.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;

/**
 * Turns a resolved directional lane count plus {@code turn:lanes} cells into the lane model.
 * Never fabricates a movement: when evidence is missing or unsupported a lane receives every
 * geometrically-available outgoing link (the schema-mandatory "unrestricted" fallback) and the
 * confidence attribute records why.
 */
public final class OsmLaneDecomposer {

    public LaneDecomposition decompose(String linkId, OsmLaneCount count, List<OsmTurnLaneCell> cells,
                                       List<String> outgoingLinkIds, MovementTurnClassifier classifier,
                                       double capacityPerLane) {
        List<OsmImportIssue> issues = new ArrayList<>();
        int laneCount = count.lanes();
        List<OsmTurnLaneCell> aligned = cells;

        if (!cells.isEmpty() && cells.size() != laneCount) {
            issues.add(issue("lane-count-mismatch",
                    "Link " + linkId + " has " + laneCount + " lanes but " + cells.size()
                            + " turn:lanes cells"));
            if (LaneConfidence.UNDETERMINED_SPLIT.equals(count.confidence())
                    || LaneConfidence.ABSENT.equals(count.confidence())) {
                laneCount = cells.size();
                aligned = cells;
            }
        }

        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create(linkId, Link.class));
        for (int i = 0; i < laneCount; i++) {
            OsmTurnLaneCell cell = i < aligned.size() ? aligned.get(i) : null;
            assignment.addLane(buildLane(linkId, i, cell, count, outgoingLinkIds, classifier,
                    capacityPerLane, issues));
        }
        return new LaneDecomposition(assignment, issues);
    }

    private Lane buildLane(String linkId, int index, OsmTurnLaneCell cell, OsmLaneCount count,
                           List<String> outgoingLinkIds, MovementTurnClassifier classifier,
                           double capacityPerLane, List<OsmImportIssue> issues) {
        Lane lane = new Lane(Id.create(linkId + "_l" + index, Lane.class));
        lane.setCapacityVehiclesPerHour(capacityPerLane);
        lane.getAttributes().putAttribute("osm:lane.count", index);
        if (count.undeterminedTotal() != null) {
            lane.getAttributes().putAttribute("osm:lanes.total", count.undeterminedTotal());
        }

        Set<String> toLinks = new LinkedHashSet<>();
        String confidence = count.confidence();

        if (cell == null || cell.empty()) {
            confidence = LaneConfidence.ABSENT;
            if (cell != null && cell.empty()) {
                issues.add(issue("empty-turn-cell", "Link " + linkId + " lane " + index + " is empty"));
            }
            toLinks.addAll(outgoingLinkIds);
        } else if (!cell.unsupportedTokens().isEmpty()) {
            confidence = LaneConfidence.UNSUPPORTED;
            issues.add(issue("unsupported-turn-token", "Link " + linkId + " lane " + index
                    + " has unsupported token(s) " + cell.unsupportedTokens()));
            toLinks.addAll(outgoingLinkIds);
        } else if (!cell.indications().isEmpty()) {
            if (LaneConfidence.UNDETERMINED_SPLIT.equals(count.confidence())
                    || LaneConfidence.ABSENT.equals(count.confidence())) {
                confidence = LaneConfidence.TURN_LANES_AUTHORITATIVE;
            }
            for (LaneTurnClass indication : cell.indications()) {
                toLinks.addAll(resolve(linkId, indication, outgoingLinkIds, classifier));
            }
            if (toLinks.isEmpty()) {
                confidence = LaneConfidence.PARTIAL;
            }
        }

        if (cell != null && cell.merge() != LaneMerge.NONE) {
            lane.getAttributes().putAttribute("osm:lane.merge",
                    cell.merge() == LaneMerge.LEFT ? "left" : "right");
            if (toLinks.isEmpty()) {
                toLinks.addAll(outgoingLinkIds);
                confidence = LaneConfidence.UNSUPPORTED;
            }
        }
        if (toLinks.isEmpty()) {
            toLinks.addAll(outgoingLinkIds);
            confidence = LaneConfidence.ABSENT;
        }
        for (String id : toLinks) {
            lane.addToLinkId(Id.create(id, Link.class));
        }
        lane.getAttributes().putAttribute("osm:lane.confidence", confidence);
        if (cell != null) {
            lane.getAttributes().putAttribute("osm:lane.rawToken", cell.raw());
        }
        lane.getAttributes().putAttribute("osm:lane.capacity.shared",
                Boolean.toString(toLinks.size() > 1));
        return lane;
    }

    private static List<String> resolve(String inLinkId, LaneTurnClass indication, List<String> outgoing,
                                        MovementTurnClassifier classifier) {
        Map<LaneTurnClass, List<String>> byClass = new LinkedHashMap<>();
        for (String out : outgoing) {
            byClass.computeIfAbsent(classifier.classify(inLinkId, out), k -> new ArrayList<>()).add(out);
        }
        List<String> direct = byClass.getOrDefault(indication, List.of());
        if (!direct.isEmpty()) {
            return direct;
        }
        // Documented, flagged fallbacks: a sharp turn with no sharp movement falls back to the base
        // left/right movement; a base turn with only a slight movement falls back to it.
        LaneTurnClass fallback = switch (indication) {
            case SHARP_LEFT -> LaneTurnClass.LEFT;
            case SHARP_RIGHT -> LaneTurnClass.RIGHT;
            case LEFT -> LaneTurnClass.SLIGHT_LEFT;
            case RIGHT -> LaneTurnClass.SLIGHT_RIGHT;
            default -> LaneTurnClass.UNKNOWN;
        };
        return byClass.getOrDefault(fallback, List.of());
    }

    private static OsmImportIssue issue(String code, String message) {
        return new OsmImportIssue(OsmIssueSeverity.WARNING, code, message, null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -o -B -Dtest=OsmLaneDecomposerTest test`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/osm/network/MovementTurnClassifier.java \
        src/main/java/com/citymodeler/matsim/models/osm/network/LaneDecomposition.java \
        src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneDecomposer.java \
        src/test/java/com/citymodeler/matsim/models/osm/network/OsmLaneDecomposerTest.java
git commit -m "feat(lanes): lane decomposer with conservative fallbacks and shared-capacity flag"
```

---

### Task 4: `laneDefinitions_v2.0` writer/reader + schema oracle

**Files:**
- Modify: `src/main/java/com/citymodeler/matsim/models/io/LanesXmlWriter.java` (rewrite body)
- Modify: `src/main/java/com/citymodeler/matsim/models/io/LanesXmlReader.java` (parse new shape)
- Modify: `src/main/resources/schemas/lanes.xsd` (permissive stub, namespace-aware)
- Create: `src/test/resources/matsim-spec/laneDefinitions_v2.0.xsd` (vendored published spec — copy from `dtd/laneDefinitions_v2.0.xsd` in the MATSim jar, with the same `THIRD-PARTY SPECIFICATION MATERIAL` header comment used by `matsim-spec/vehicleDefinitions_v2.0.xsd`)
- Modify: `src/test/resources/fixtures/lanes.xml` (migrate to the new shape)
- Test: `src/test/java/com/citymodeler/matsim/models/io/LanesXmlTest.java` (rewrite assertions)
- Test: `src/test/java/com/citymodeler/matsim/models/io/MatsimLaneSpecValidationTest.java` (new, mirrors `MatsimVehicleSpecValidationTest`)
- Modify: `THIRD_PARTY_NOTICES.md` (add `laneDefinitions_v2.0.xsd`)

**Interfaces:**
- Consumes: `Lanes`, `LanesToLinkAssignment`, `Lane`; `XmlSupport` helpers.
- Produces: `LanesXmlWriter.write(Lanes, Path|OutputStream)`, `writeToString(Lanes)` emitting the published shape; `LanesXmlReader.read(...)` parsing it.

- [ ] **Step 1: Vendor the published XSD and write the failing spec test**

Extract and copy (the jar is already at `/home/ashraf/.m2/repository/org/matsim/matsim/2027.0-2026w25/matsim-2027.0-2026w25.jar`):

```bash
mkdir -p src/test/resources/matsim-spec
unzip -p /home/ashraf/.m2/repository/org/matsim/matsim/2027.0-2026w25/matsim-2027.0-2026w25.jar \
  dtd/laneDefinitions_v2.0.xsd > src/test/resources/matsim-spec/laneDefinitions_v2.0.xsd
```
Then prepend the same third-party header comment block used in `src/test/resources/matsim-spec/vehicleDefinitions_v2.0.xsd`, and add the file to `THIRD_PARTY_NOTICES.md` alongside the existing MATSim spec entries.

`MatsimLaneSpecValidationTest.java`:

```java
package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;

/** Interoperability oracle: our lane output must validate against the published MATSim v2.0 schema. */
class MatsimLaneSpecValidationTest {

    @Test
    void canonicalWriterOutputValidatesAgainstPublishedSchema() throws Exception {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.setCapacityVehiclesPerHour(900.0);
        lane.setAlignment("0");
        lane.getAttributes().putAttribute("osm:lane.confidence", "present");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        String xml = new LanesXmlWriter().writeToString(lanes);
        var factory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // systemId must be the resource URL so the XSD's <xs:include schemaLocation="matsimCommon.xsd"/>
        // resolves relative to it (matsimCommon.xsd is already vendored under matsim-spec/).
        var schemaUrl = getClass().getClassLoader().getResource("matsim-spec/laneDefinitions_v2.0.xsd");
        var schema = factory.newSchema(schemaUrl);
        assertDoesNotThrow(() -> schema.newValidator()
                .validate(new StreamSource(new StringReader(xml))));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -o -B -Dtest=MatsimLaneSpecValidationTest test`
Expected: FAIL — writer still emits `<lanes>/<assignment>` and `xsi:schemaLocation` is absent.

- [ ] **Step 3: Rewrite the writer**

Replace `LanesXmlWriter.document` (keep the public methods) with:

```java
    private static final String MATSIM_NAMESPACE = "http://www.matsim.org/files/dtd";
    private static final String SCHEMA_LOCATION =
            MATSIM_NAMESPACE + " " + MATSIM_NAMESPACE + "/laneDefinitions_v2.0.xsd";

    private Document document(Lanes lanes) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElementNS(MATSIM_NAMESPACE, "laneDefinitions");
        root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation",
                SCHEMA_LOCATION);
        document.appendChild(root);
        for (LanesToLinkAssignment assignment : lanes.getLanesToLinkAssignments().values()) {
            Element assignmentElement = document.createElementNS(MATSIM_NAMESPACE, "lanesToLinkAssignment");
            assignmentElement.setAttribute("linkIdRef", assignment.getLinkId().toString());
            root.appendChild(assignmentElement);
            for (Lane lane : assignment.getLanes().values()) {
                Element laneElement = document.createElementNS(MATSIM_NAMESPACE, "lane");
                laneElement.setAttribute("id", lane.getId().toString());

                Element leadsTo = document.createElementNS(MATSIM_NAMESPACE, "leadsTo");
                for (Id<Link> toLink : lane.getToLinkIds()) {
                    Element toLinkElement = document.createElementNS(MATSIM_NAMESPACE, "toLink");
                    toLinkElement.setAttribute("refId", toLink.toString());
                    leadsTo.appendChild(toLinkElement);
                }
                for (Id<Lane> toLane : lane.getToLaneIds()) {
                    Element toLaneElement = document.createElementNS(MATSIM_NAMESPACE, "toLane");
                    toLaneElement.setAttribute("refId", toLane.toString());
                    leadsTo.appendChild(toLaneElement);
                }
                laneElement.appendChild(leadsTo);

                if (lane.getCapacityVehiclesPerHour() > 0.0) {
                    Element capacity = document.createElementNS(MATSIM_NAMESPACE, "capacity");
                    capacity.setAttribute("vehiclesPerHour",
                            Double.toString(lane.getCapacityVehiclesPerHour()));
                    laneElement.appendChild(capacity);
                }
                if (lane.getStartsAtMeterFromLinkEnd() > 0.0) {
                    Element startsAt = document.createElementNS(MATSIM_NAMESPACE, "startsAt");
                    startsAt.setAttribute("meterFromLinkEnd",
                            Double.toString(lane.getStartsAtMeterFromLinkEnd()));
                    laneElement.appendChild(startsAt);
                }
                Element alignment = document.createElementNS(MATSIM_NAMESPACE, "alignment");
                alignment.setTextContent(lane.getAlignment() != null ? lane.getAlignment() : "0");
                laneElement.appendChild(alignment);

                XmlSupport.appendAttributes(document, laneElement, lane.getAttributes(), MATSIM_NAMESPACE);
                assignmentElement.appendChild(laneElement);
            }
        }
        return document;
    }
```

Add `import com.citymodeler.matsim.models.network.Link;`. Remove the now-unused `join(...)`/`StringJoiner` import usage (keep the file checkstyle-clean; checkstyle warns, not fails).

- [ ] **Step 4: Rewrite the reader**

Replace `LanesXmlReader.read(Element)` body with:

```java
    private Lanes read(Element root) {
        Lanes lanes = new Lanes();
        XmlSupport.readAttributes(root, lanes.getAttributes());
        for (Element assignmentElement : XmlSupport.children(root, "lanesToLinkAssignment")) {
            LanesToLinkAssignment assignment = new LanesToLinkAssignment(
                    Id.create(XmlSupport.attr(assignmentElement, "linkIdRef"), Link.class));
            for (Element laneElement : XmlSupport.children(assignmentElement, "lane")) {
                Lane lane = new Lane(Id.create(XmlSupport.attr(laneElement, "id"), Lane.class));
                Element leadsTo = XmlSupport.child(laneElement, "leadsTo");
                if (leadsTo != null) {
                    for (Element toLink : XmlSupport.children(leadsTo, "toLink")) {
                        lane.addToLinkId(Id.create(XmlSupport.attr(toLink, "refId"), Link.class));
                    }
                    for (Element toLane : XmlSupport.children(leadsTo, "toLane")) {
                        lane.addToLaneId(Id.create(XmlSupport.attr(toLane, "refId"), Lane.class));
                    }
                }
                Element capacity = XmlSupport.child(laneElement, "capacity");
                if (capacity != null) {
                    lane.setCapacityVehiclesPerHour(
                            XmlSupport.optionalDouble(capacity, "vehiclesPerHour", 0.0));
                }
                Element startsAt = XmlSupport.child(laneElement, "startsAt");
                if (startsAt != null) {
                    lane.setStartsAtMeterFromLinkEnd(
                            XmlSupport.optionalDouble(startsAt, "meterFromLinkEnd", 0.0));
                }
                Element alignment = XmlSupport.child(laneElement, "alignment");
                if (alignment != null) {
                    lane.setAlignment(alignment.getTextContent());
                }
                XmlSupport.readAttributes(laneElement, lane.getAttributes());
                assignment.addLane(lane);
            }
            lanes.addAssignment(assignment);
        }
        return lanes;
    }
```

Update `SCHEMA` constant to `/schemas/lanes.xsd` (unchanged) and keep the legacy comma-list helpers removed if unused.

- [ ] **Step 5: Migrate the permissive stub + fixture, rewrite round-trip test**

`src/main/resources/schemas/lanes.xsd`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Repo-local permissive stub; real conformance is verified in test scope against the
     vendored published schema (src/test/resources/matsim-spec/laneDefinitions_v2.0.xsd). -->
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://www.matsim.org/files/dtd"
           xmlns="http://www.matsim.org/files/dtd"
           elementFormDefault="qualified">
    <xs:element name="laneDefinitions" type="xs:anyType" />
</xs:schema>
```

`src/test/resources/fixtures/lanes.xml`: rewrite as two `lanesToLinkAssignment` blocks with nested `leadsTo`, `capacity`, `startsAt`, `alignment`, `attributes` in XSD order.

Rewrite `LanesXmlTest` to assert: two assignments, `lane l1_l0`/`l1_l1`, `toLink` refs, capacity, and `osm:lane.confidence` round-trip.

- [ ] **Step 6: Run the IO tests**

Run: `mvn -o -B -Dtest='LanesXmlTest,MatsimLaneSpecValidationTest,XmlSchemaValidationTest' test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/io/LanesXmlWriter.java \
        src/main/java/com/citymodeler/matsim/models/io/LanesXmlReader.java \
        src/main/resources/schemas/lanes.xsd \
        src/test/resources/matsim-spec/laneDefinitions_v2.0.xsd \
        src/test/resources/fixtures/lanes.xml \
        src/test/java/com/citymodeler/matsim/models/io/LanesXmlTest.java \
        src/test/java/com/citymodeler/matsim/models/io/MatsimLaneSpecValidationTest.java \
        THIRD_PARTY_NOTICES.md
git commit -m "feat(lanes): emit published laneDefinitions_v2.0 shape with schema oracle"
```

---

### Task 5: Geometry turn classifier + bundle wiring

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/GeometryTurnClassifier.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/LaneDefinitionBuilder.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/bundle/OsmGtfsBundleRunner.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/GeometryTurnClassifierTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/bundle/OsmGtfsBundleRunnerLanesTest.java`

**Interfaces:**
- Consumes: Task 1/2/3; `OsmSimplifiedNetwork` (`network()`, `collapsedLinksByLinkId()`, `geometryStore()`), `OsmImportResult` (`ways()`), `OsmGeometryStore.geometryForLink(String)`, `OsmPolyline`, `OsmNetworkBuildConfig.resolveRule`.
- Produces:
  - `GeometryTurnClassifier(Network network, OsmGeometryStore geometryStore)` implementing `MovementTurnClassifier`.
  - `LaneDefinitionBuilder.build(Network network, Map<String, OsmLinkRef> refs, Map<String, OsmWayRecord> ways, OsmGeometryStore geometryStore, OsmNetworkBuildConfig config) -> LaneDefinitionResult`.
  - `record LaneDefinitionResult(Lanes lanes, List<OsmImportIssue> issues)`.
  - `OsmGtfsBundleRunner.LANE_DEFINITIONS_FILE = "laneDefinitions.xml"`; `BundleResult.lanesFile`, `BundleResult.laneAssignments`.

- [ ] **Step 1: Write the failing classifier + builder tests**

`GeometryTurnClassifierTest.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Network;

class GeometryTurnClassifierTest {

    @Test
    void classifiesStraightLeftAndRightFromGeometry() {
        Network network = new Network();
        network.createNode("c", 0, 0);
        network.createNode("north", 0, 100);
        network.createNode("east", 100, 0);
        network.createNode("west", -100, 0);
        network.createLink("in", "c", "c", 1, 900, 10, 1, java.util.Set.of("car"));
        network.createLink("straight", "c", "north", 100, 900, 10, 1, java.util.Set.of("car"));
        network.createLink("right", "c", "east", 100, 900, 10, 1, java.util.Set.of("car"));
        network.createLink("left", "c", "west", 100, 900, 10, 1, java.util.Set.of("car"));

        OsmGeometryStore geometry = new OsmGeometryStore(Map.of(
                "in", new OsmPolyline(List.of(new Coord(0, -100), new Coord(0, 0))),
                "straight", new OsmPolyline(List.of(new Coord(0, 0), new Coord(0, 100))),
                "right", new OsmPolyline(List.of(new Coord(0, 0), new Coord(100, 0))),
                "left", new OsmPolyline(List.of(new Coord(0, 0), new Coord(-100, 0)))));

        GeometryTurnClassifier classifier = new GeometryTurnClassifier(network, geometry);
        assertEquals(LaneTurnClass.THROUGH, classifier.classify("in", "straight"));
        assertEquals(LaneTurnClass.RIGHT, classifier.classify("in", "right"));
        assertEquals(LaneTurnClass.LEFT, classifier.classify("in", "left"));
    }
}
```

`OsmGtfsBundleRunnerLanesTest.java` (uses the existing `src/test/resources/osm/access-oneway-lanes.osm`):

```java
package com.citymodeler.matsim.models.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.io.LanesXmlReader;
import com.citymodeler.matsim.models.lanes.Lanes;

class OsmGtfsBundleRunnerLanesTest {

    @Test
    void bundleAlwaysWritesLaneDefinitionsWithOneLanePerLink(@TempDir Path out) throws Exception {
        Path osm = Path.of("src/test/resources/osm/access-oneway-lanes.osm");
        OsmGtfsBundleRunner.BundleResult result =
                new OsmGtfsBundleRunner().run(osm, null, null, out);

        assertNotNull(result.lanesFile());
        assertTrue(Files.exists(result.lanesFile()));
        assertTrue(result.laneAssignments() >= result.baseNetworkLinks(),
                "every emitted link must carry at least one lane");

        Lanes lanes = new LanesXmlReader().read(result.lanesFile());
        assertEquals(result.laneAssignments(), lanes.getLanesToLinkAssignments().size());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -o -B -Dtest='GeometryTurnClassifierTest,OsmGtfsBundleRunnerLanesTest' test`
Expected: FAIL — classifier, builder, `lanesFile`, `laneAssignments` do not exist.

- [ ] **Step 3: Implement the classifier**

```java
package com.citymodeler.matsim.models.osm.network;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;

/**
 * Classifies a turning movement from link geometry: the incoming link's final segment versus the
 * outgoing link's first segment. Falls back to endpoint node coordinates when geometry is absent.
 * Buckets the signed bearing delta into sharp/left/slight/through/slight/right/sharp.
 */
public final class GeometryTurnClassifier implements MovementTurnClassifier {

    private final Network network;
    private final OsmGeometryStore geometryStore;

    public GeometryTurnClassifier(Network network, OsmGeometryStore geometryStore) {
        this.network = network;
        this.geometryStore = geometryStore;
    }

    @Override
    public LaneTurnClass classify(String inLinkId, String outLinkId) {
        Coord[] in = segment(inLinkId, true);
        Coord[] out = segment(outLinkId, false);
        if (in == null || out == null) {
            return LaneTurnClass.UNKNOWN;
        }
        double ix = in[1].getX() - in[0].getX();
        double iy = in[1].getY() - in[0].getY();
        double ox = out[1].getX() - out[0].getX();
        double oy = out[1].getY() - out[0].getY();
        double inHeading = Math.atan2(iy, ix);
        double outHeading = Math.atan2(oy, ox);
        double delta = Math.toDegrees(normalize(outHeading - inHeading));
        double abs = Math.abs(delta);
        if (abs < 20) {
            return LaneTurnClass.THROUGH;
        }
        if (abs > 150) {
            return LaneTurnClass.REVERSE;
        }
        boolean left = delta > 0;
        if (abs < 45) {
            return left ? LaneTurnClass.SLIGHT_LEFT : LaneTurnClass.SLIGHT_RIGHT;
        }
        if (abs > 120) {
            return left ? LaneTurnClass.SHARP_LEFT : LaneTurnClass.SHARP_RIGHT;
        }
        return left ? LaneTurnClass.LEFT : LaneTurnClass.RIGHT;
    }

    private static double normalize(double radians) {
        double r = radians;
        while (r > Math.PI) {
            r -= 2 * Math.PI;
        }
        while (r <= -Math.PI) {
            r += 2 * Math.PI;
        }
        return r;
    }

    private Coord[] segment(String linkId, boolean last) {
        var geometry = geometryStore.geometryForLink(linkId);
        if (geometry.isPresent() && geometry.get().points().size() >= 2) {
            var points = geometry.get().points();
            return last
                    ? new Coord[]{points.get(points.size() - 2), points.get(points.size() - 1)}
                    : new Coord[]{points.get(0), points.get(1)};
        }
        Link link = network.getLinks().get(com.citymodeler.matsim.models.api.Id.create(
                linkId, Link.class));
        if (link == null || link.getFromNode() == null || link.getToNode() == null) {
            return null;
        }
        return new Coord[]{link.getFromNode().getCoord(), link.getToNode().getCoord()};
    }
}
```

- [ ] **Step 4: Implement the builder**

`LaneDefinitionResult.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.List;

import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.osm.OsmImportIssue;

/** Lanes for a whole network plus decomposition issues. */
public record LaneDefinitionResult(Lanes lanes, List<OsmImportIssue> issues) {
    public LaneDefinitionResult {
        issues = List.copyOf(issues);
    }
}
```

`LaneDefinitionBuilder.java`:

```java
package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/** Builds a {@link Lanes} model for every link of an emitted network. */
public final class LaneDefinitionBuilder {

    private final OsmDirectionalLaneResolver countResolver = new OsmDirectionalLaneResolver();
    private final OsmTurnLaneParser turnParser = new OsmTurnLaneParser();
    private final OsmLaneDecomposer decomposer = new OsmLaneDecomposer();

    public LaneDefinitionResult build(Network network, Map<String, OsmLinkRef> refs,
                                      Map<String, OsmWayRecord> ways, OsmGeometryStore geometryStore,
                                      OsmNetworkBuildConfig config) {
        Lanes lanes = new Lanes();
        List<OsmImportIssue> issues = new ArrayList<>();
        MovementTurnClassifier classifier = new GeometryTurnClassifier(network, geometryStore);

        Map<String, List<String>> outgoingByNode = new TreeMap<>();
        for (Link link : network.getLinks().values()) {
            outgoingByNode.computeIfAbsent(link.getFromNodeId().toString(), k -> new ArrayList<>())
                    .add(link.getId().toString());
        }
        outgoingByNode.values().forEach(list -> list.sort(String::compareTo));

        for (String linkId : new TreeMap<>(refs).keySet()) {
            Link link = network.getLinks().get(Id.create(linkId, Link.class));
            OsmLinkRef ref = refs.get(linkId);
            OsmWayRecord way = ways.get(ref.osmWayId());
            if (link == null || way == null) {
                continue;
            }
            OsmWayRule rule = config.resolveRule(way.tags());
            if (rule == null) {
                continue;
            }
            boolean oneway = isOneway(way);
            OsmLaneCount count = countResolver.resolve(way.tags(), ref.forward(), oneway);
            List<OsmTurnLaneCell> cells = turnParser.parse(turnLanes(way, ref.forward(), oneway));
            List<String> outgoing = outgoingByNode.getOrDefault(
                    link.getToNodeId().toString(), List.of());
            if (outgoing.isEmpty()) {
                // A lane must carry at least one leadsTo (XSD-mandatory); a link whose to-node has no
                // outgoing links (dead end / isolated) cannot produce a valid assignment. Skip it with
                // a visible issue rather than fabricating a movement or emitting an invalid lane.
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "lane-no-outgoing",
                        "Link " + linkId + " has no outgoing links at its to-node; no lane assignment",
                        null));
                continue;
            }

            LaneDecomposition decomposition = decomposer.decompose(linkId, count, cells, outgoing,
                    classifier, rule.capacityPerLane());
            lanes.addAssignment(decomposition.assignment());
            issues.addAll(decomposition.issues());
        }
        return new LaneDefinitionResult(lanes, issues);
    }

    private static boolean isOneway(OsmWayRecord way) {
        String oneway = way.tags().get("oneway");
        return "yes".equals(oneway) || "1".equals(oneway) || "true".equals(oneway);
    }

    private static String turnLanes(OsmWayRecord way, boolean forward, boolean oneway) {
        String directional = way.tags().get("turn:lanes" + (forward ? ":forward" : ":backward"));
        if (directional != null && !directional.isBlank()) {
            return directional;
        }
        // A bare turn:lanes on a bidirectional way is only applied when the resolver applied the
        // even split (both directions share the same per-direction cell list); the decomposer's
        // count/cell reconciliation (Task 3) is the guard against a bad alignment.
        return way.tags().get("turn:lanes");
    }
}
```

Add a short class javadoc note that a bare `turn:lanes` on a bidirectional way is applied only
left→right in the travel direction and that mismatch reconciliation (Task 3) is the guard.

- [ ] **Step 5: Wire the bundle**

In `OsmGtfsBundleRunner`:
1. add `public static final String LANE_DEFINITIONS_FILE = "laneDefinitions.xml";`
2. add `Path lanesFile` and `int laneAssignments` to `BundleResult` (place `lanesFile` after `facilitiesFile`, `laneAssignments` after `baseNetworkLinks`).
3. After `OsmSimplifiedNetwork simplified = ...` and before/after writing `network.xml`, build the refs map and lanes:

```java
Map<String, com.citymodeler.matsim.models.osm.network.OsmLinkRef> refs = new java.util.LinkedHashMap<>();
for (var entry : simplified.collapsedLinksByLinkId().entrySet()) {
    var source = entry.getValue().sourceSegments().get(0);
    refs.put(entry.getKey(), new com.citymodeler.matsim.models.osm.network.OsmLinkRef(
            entry.getKey(), source.osmWayId(), source.segmentIndex(), source.forward()));
}
var laneResult = new com.citymodeler.matsim.models.osm.network.LaneDefinitionBuilder()
        .build(baseNetwork, refs, importResult.ways(), simplified.geometryStore(), networkConfig);
warnings.addAll(laneResult.issues().stream()
        .map(com.citymodeler.matsim.models.osm.OsmImportIssue::message).toList());
Path lanesFile = outputDirectory.resolve(LANE_DEFINITIONS_FILE);
new LanesXmlWriter().write(laneResult.lanes(), lanesFile);
int laneAssignments = laneResult.lanes().getLanesToLinkAssignments().size();
```
4. pass `lanesFile` and `laneAssignments` into the `BundleResult` constructor and update the `main` summary line.

- [ ] **Step 6: Run the tests**

Run: `mvn -o -B -Dtest='GeometryTurnClassifierTest,OsmGtfsBundleRunnerLanesTest' test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/osm/network/GeometryTurnClassifier.java \
        src/main/java/com/citymodeler/matsim/models/osm/network/LaneDefinitionResult.java \
        src/main/java/com/citymodeler/matsim/models/osm/network/LaneDefinitionBuilder.java \
        src/main/java/com/citymodeler/matsim/models/bundle/OsmGtfsBundleRunner.java \
        src/test/java/com/citymodeler/matsim/models/osm/network/GeometryTurnClassifierTest.java \
        src/test/java/com/citymodeler/matsim/models/bundle/OsmGtfsBundleRunnerLanesTest.java
git commit -m "feat(lanes): geometry turn classifier and laneDefinitions bundle output"
```

---

### Task 6: Mapping tests — continuity, turn restrictions, artificial links, child stops

**Files:**
- Test: `src/test/java/com/citymodeler/matsim/models/mapping/MappedRouteContinuityTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/mapping/TurnRestrictionRoutingTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/mapping/ArtificialLinkTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/mapping/MappedChildStopsTest.java`
- Modify (only if a test surfaces a bug): the relevant `mapping/*.java`.

**Interfaces:**
- Consumes: `TransitNetworkMapper`, `TransitMappingResult`, `MappingReport`, `RoutePathFinder`, `ArtificialLinkFactory`, `ChildStopCreator`, `DisallowedNextLinks`.
- Produces: passing regression tests. No new production types unless a bug is found.

- [ ] **Step 1: Write the four failing/regression tests**

Reuse the helper style in `TransitNetworkMapperTest`. Each test builds a tiny `Network`, a one-route `TransitSchedule`, maps it, and asserts:

- `MappedRouteContinuityTest`: for a chain of stops, `route.getNetworkRoute()` is non-null and every consecutive pair `(from.toNode, to.fromNode)` connects; a gap is always bridged by a `pt_` link.
- `TurnRestrictionRoutingTest`: a two-stop route where the only path requires a disallowed sequence for the route mode yields an artificial connector (restriction honored) and no restricted link sequence appears verbatim.
- `ArtificialLinkTest`: `createLoop` yields `length>=1`, `capacity>0`, `freespeed>0`, `lanes>=1`, `artificial=true`, `pt_` id; mapping a schedule twice with shuffled stop/route insertion order yields the same artificial-link count.
- `MappedChildStopsTest`: after mapping, every `TransitRouteStop` references an id of the form `parent.link:linkId`; the child facility exists with `linkId` set and `gtfs:parentStopId` metadata.

- [ ] **Step 2: Run them**

Run: `mvn -o -B -Dtest='MappedRouteContinuityTest,TurnRestrictionRoutingTest,ArtificialLinkTest,MappedChildStopsTest' test`
Expected: PASS; any FAIL is a real bug.

- [ ] **Step 3: Fix surfaced bugs minimally**

If a test fails, fix the smallest cause in `mapping/*` and re-run. Do not weaken the assertion to make it pass. Common expected fixes: child-stop metadata (`gtfs:parentStopId`) missing, or a continuity gap not bridged.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/citymodeler/matsim/models/mapping src/main/java/com/citymodeler/matsim/models/mapping
git commit -m "test(mapping): continuity, turn-restriction, artificial-link, child-stop verification"
```

---

### Task 7: Mapping tests — determinism, schedule round-trip, vehicles

**Files:**
- Test: `src/test/java/com/citymodeler/matsim/models/mapping/DeterministicOutputTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/gtfs/GtfsScheduleRoundTripTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/gtfs/VehicleDefinitionsTest.java`
- Modify (only if a test surfaces a bug): `gtfs/*` or `io/*`.

**Interfaces:**
- Consumes: `GtfsImporter`, `GtfsTransitScheduleBuilder`, `GtfsTransitBuildResult`, `TransitScheduleXmlWriter/Reader`, `VehiclesXmlWriter/Reader`, `TransitNetworkMapper`.
- Produces: passing verification tests.

- [ ] **Step 1: Write the three tests**

- `DeterministicOutputTest`: import the same GTFS fixture twice with shuffled feed row order (reuse `GtfsPipelineIntegrationTest.deterministicOutputUnderShuffledInput` patterns) and assert byte-identical `TransitScheduleXmlWriter.writeToString`.
- `GtfsScheduleRoundTripTest`: build → write → `TransitScheduleXmlReader` → assert lines/routes/departures/stops and stop coordinates survive; write again and assert byte-identical.
- `VehicleDefinitionsTest`: every departure's `getVehicleId()` resolves in `VehicleDefinitions.getVehicles()`; every vehicle's type resolves in `getVehicleTypes()`.

- [ ] **Step 2: Run them**

Run: `mvn -o -B -Dtest='DeterministicOutputTest,GtfsScheduleRoundTripTest,VehicleDefinitionsTest' test`
Expected: PASS.

- [ ] **Step 3: Fix surfaced bugs minimally, then commit**

```bash
git add src/test/java src/main/java
git commit -m "test(gtfs): schedule round-trip, vehicle referential integrity, determinism"
```

---

### Task 8: GeoJSON stop→link export + numeric assertions

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/mapping/MappingGeoJsonWriter.java`
- Test: `src/test/java/com/citymodeler/matsim/models/mapping/MappingGeoJsonWriterTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/mapping/StopLinkPhysicalAssertionTest.java`

**Interfaces:**
- Consumes: `TransitSchedule.getFacilities()`, `TransitStopFacility` (`getCoord`, `getLinkId`, `getName`, `gtfs:lon/gtfs:lat`), `Network`/`Link` (`getFromNode`/`getToNode`, `osm:name`/`osm:sourceNames`).
- Produces:
  - `MappingGeoJsonWriter.writeToString(TransitSchedule schedule, Network network) -> String` — a `FeatureCollection` of `Point` features for each stop facility (chosen link id and coordinates as properties).
  - Test-only numeric assertions.

- [ ] **Step 1: Write the failing tests**

`MappingGeoJsonWriterTest`: build a schedule + network (helpers as in `TransitNetworkMapperTest`), map it, write GeoJSON, and assert the text is valid JSON (`com.fasterxml.jackson.databind.ObjectMapper` is already a dependency), has one feature per facility, and each feature carries a `linkId` property.

`StopLinkPhysicalAssertionTest`: a stop named after a road (`osm:name` on the candidate link) snaps to that link; assert `StopCandidateScorer.nameSimilarity(stopName, chosenLink) > 0.5` and `distanceMeters < config.maxLinkCandidateDistanceMeters()`.

- [ ] **Step 2: Run to verify failure**

Run: `mvn -o -B -Dtest='MappingGeoJsonWriterTest,StopLinkPhysicalAssertionTest' test`
Expected: FAIL — writer does not exist.

- [ ] **Step 3: Implement the writer**

Plain string building (no new dependency), deterministic order (sorted facility ids), `ObjectMapper` only for escaping names/ids:

```java
package com.citymodeler.matsim.models.mapping;

// build: {"type":"FeatureCollection","features":[
//   {"type":"Feature","geometry":{"type":"Point","coordinates":[x,y]},
//    "properties":{"facilityId":..,"linkId":..,"name":..}}, ...]}
```

Full class: iterate `new TreeMap<>(schedule.getFacilities())`, skip null coords, write features; expose `write(Path)` and `writeToString(...)` mirroring other writers.

- [ ] **Step 4: Run to verify pass, then commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/mapping/MappingGeoJsonWriter.java \
        src/test/java/com/citymodeler/matsim/models/mapping/MappingGeoJsonWriterTest.java \
        src/test/java/com/citymodeler/matsim/models/mapping/StopLinkPhysicalAssertionTest.java
git commit -m "feat(mapping): GeoJSON stop-link export and physical stop-link assertions"
```

---

### Task 9: Black-box comparison doc + full gate

**Files:**
- Modify: `docs/pt2matsim-blackbox-comparison.md`
- Possibly run: `tools/oracle/run_reference_oracle.sh`

**Interfaces:** none (documentation + verification).

- [ ] **Step 1: Add the lane/mapping comparison section**

Run the bundle on the Toronto fixture and, using the existing oracle script, record side-by-side structural counts (base network nodes/links, mapped schedule route continuity) in `docs/pt2matsim-blackbox-comparison.md`. Name pt2MATSim transparently (no vendored config; the harness generates config at runtime and overrides only the three paths). Report any large divergence as an open item, not a hidden failure.

- [ ] **Step 2: Run the full gate**

Run: `source /home/ashraf/anaconda3/etc/profile.d/conda.sh && conda activate base && mvn -o -B clean verify`
Expected: all tests pass, checkstyle 0 violations, SpotBugs 0 (High).

- [ ] **Step 3: Commit**

```bash
git add docs/pt2matsim-blackbox-comparison.md
git commit -m "docs: lane/mapping black-box comparison and full-gate note"
```

- [ ] **Step 4: Open the single PR** (only when the user asks)

`git push -u origin feat/lanes-and-mapping` then `gh pr create` against `main` with a summary, the spec link, and the gate result.

---

## Self-Review

**Spec coverage:**
- §1a directional resolution → Task 1.
- §1b token semantics → Task 2 + Task 3 fallback/merge handling.
- §1c reconciliation → Task 3.
- §1d absent turn:lanes → Task 3 (all-outgoing + `absent`).
- §1e capacity sharing → Task 3 (`osm:lane.capacity.shared`, physical capacity unchanged).
- §1f ids/provenance → Tasks 2/3.
- Part 2 laneDefinitions IO + XSD order/mandatory fields → Task 4.
- Part 3 bundle output + BundleResult → Task 5.
- Part 4 seven mapping tests → Tasks 6/7; GeoJSON + numeric → Task 8; black-box → Task 9.
- Part 5 via-way out of scope → not implemented (correct).

**Placeholder scan:** no TBD/TODO; every code step carries real code except the deliberately short GeoJSON writer body (fully specified by the JSON shape and iteration rule) and the mapping test bodies (specified by exact assertions to make, because their fixtures are the existing test helpers).

**Type consistency:** `LaneConfidence` constants, `OsmLaneCount`, `LaneTurnClass`, `LaneMerge`, `OsmTurnLaneCell`, `MovementTurnClassifier`, `LaneDecomposition`, `LaneDefinitionResult` are defined once and used with identical signatures in later tasks. `Lane.getAlignment()` is the pre-existing `String` field; the writer emits it as an `xs:int` text node (default `"0"`).
