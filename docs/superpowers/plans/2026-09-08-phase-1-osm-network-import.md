# Phase 1 OSM Network Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the OSM-to-queryable-cleaned-MATSim-network phase that later GTFS transit mapping can rely on.

**Architecture:** Phase 1 is split into six independently reviewable work packages: import contract/provenance, OSM XML parsing, directed network construction, metadata hint extraction, geometry simplification, and turn-restriction/cleaning/query indexes. The implementation keeps OSM source records and sidecar metadata separate from compact MATSim network POJOs so CityModeler can query rich OSM context without bloating every network XML output.

**Tech Stack:** Java 17, JUnit 5, StAX XML parsing, existing `Network`/`Node`/`Link` POJOs, existing `Attributes`, existing proj4j dependency, no MATSim runtime, no Pt2MATSim, no GPL source-derived code.

**Spec:** `docs/superpowers/specs/2026-09-08-gtfs-transit-mapping-clean-room-design.md`

## Global Constraints

- Keep `matsim-models` independent: no `org.matsim.*` imports in main source.
- Do not add Pt2MATSim, MATSim, or MATSim contrib dependencies to `pom.xml`.
- Do not inspect, copy, translate, or imitate Pt2MATSim Java source code.
- Do not inspect, copy, translate, or imitate MATSim GPL Java source code.
- Use OSM official docs, ODbL guidance, GTFS docs, MATSim file-format docs, and synthetic fixtures only.
- Use OSM XML input for v1; PBF is out of scope for Phase 1 implementation.
- Preserve OSM provenance and ODbL attribution metadata in generated network artifacts.
- Default geometry mode is `PRESERVE_AS_LINK_GEOMETRY`.
- Use a dependency-free uniform grid spatial index for v1.
- Keep Phase 1 outputs deterministic: stable map ordering, stable generated ids, stable issue ordering.
- Use synthetic OSM fixtures only in tests unless a permissively licensed public fixture is explicitly documented.

---

## File Structure

Create these new packages and files.

Core OSM import contract:

- `src/main/java/com/citymodeler/matsim/models/osm/OsmElementType.java`: enum for `NODE`, `WAY`, `RELATION`.
- `src/main/java/com/citymodeler/matsim/models/osm/OsmElementId.java`: typed OSM id value object.
- `src/main/java/com/citymodeler/matsim/models/osm/OsmTagSet.java`: immutable tag wrapper with typed getters.
- `src/main/java/com/citymodeler/matsim/models/osm/OsmIssueSeverity.java`: enum for `INFO`, `WARNING`, `ERROR`.
- `src/main/java/com/citymodeler/matsim/models/osm/OsmImportIssue.java`: deterministic issue record.
- `src/main/java/com/citymodeler/matsim/models/osm/OsmProvenance.java`: OSM source/license metadata.
- `src/main/java/com/citymodeler/matsim/models/osm/OsmImportConfig.java`: parser config and CRS config.
- `src/main/java/com/citymodeler/matsim/models/osm/OsmImportResult.java`: parsed OSM source-record container.
- `src/main/java/com/citymodeler/matsim/models/osm/OsmNetworkImporter.java`: public parser entry point.

OSM source records:

- `src/main/java/com/citymodeler/matsim/models/osm/model/OsmNodeRecord.java`: source node with lon/lat, projected coord, tags.
- `src/main/java/com/citymodeler/matsim/models/osm/model/OsmWayRecord.java`: source way with node refs and tags.
- `src/main/java/com/citymodeler/matsim/models/osm/model/OsmRelationRecord.java`: source relation with members and tags.
- `src/main/java/com/citymodeler/matsim/models/osm/model/OsmRelationMemberRecord.java`: relation member.

OSM network build:

- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmGeometryMode.java`: enum for geometry behavior.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmWayRule.java`: configurable conversion rule.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkBuildConfig.java`: builder config.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkBuildResult.java`: cleaned network plus sidecars.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmMatsimNetworkBuilder.java`: public builder entry point.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmModeAccessResolver.java`: access/oneway/mode resolver.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmSpeedResolver.java`: maxspeed/default resolver.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneResolver.java`: lane count resolver.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmGeneratedIds.java`: stable generated network id helper.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmGeometryStore.java`: link id to polyline sidecar.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkSimplifier.java`: routing-node retention and link segmentation.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkCleaner.java`: component and invalid-link cleanup.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmStopHintExtractor.java`: stop/platform/station hint extraction.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneHintExtractor.java`: lane/intersection hint extraction.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmTurnRestrictionReader.java`: relation-to-restriction input conversion.

Phase 1 sidecar records:

- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmStopKind.java`: enum for stop/platform/station kinds.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmStopHint.java`: OSM transit stop hint.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneHint.java`: link lane hint.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmIntersectionLaneHint.java`: node-level lane/intersection hint.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLinkRef.java`: source-way-to-link mapping.
- `src/main/java/com/citymodeler/matsim/models/osm/network/OsmPolyline.java`: immutable projected polyline.

Network turn restrictions and indexes:

- `src/main/java/com/citymodeler/matsim/models/network/turnrestrictions/DisallowedNextLinks.java`: local MATSim-compatible restriction value object.
- `src/main/java/com/citymodeler/matsim/models/network/turnrestrictions/TurnRestrictionIndex.java`: routing/query index for restrictions.
- `src/main/java/com/citymodeler/matsim/models/network/index/NetworkQueryIndex.java`: public read-only query facade.
- `src/main/java/com/citymodeler/matsim/models/network/index/NetworkQueryIndexBuilder.java`: index builder.
- `src/main/java/com/citymodeler/matsim/models/network/index/LinkSpatialIndex.java`: uniform grid link spatial index.
- `src/main/java/com/citymodeler/matsim/models/network/index/NodeSpatialIndex.java`: uniform grid node spatial index.
- `src/main/java/com/citymodeler/matsim/models/network/index/StopHintIndex.java`: stop hint spatial index.
- `src/main/java/com/citymodeler/matsim/models/network/index/LaneHintIndex.java`: link/node lane hint lookup.
- `src/main/java/com/citymodeler/matsim/models/network/index/NearestLink.java`: nearest-link query result.
- `src/main/java/com/citymodeler/matsim/models/network/index/NearestNode.java`: nearest-node query result.

Modify existing files:

- `src/main/java/com/citymodeler/matsim/models/io/AttributesSerializer.java`: add class hint support for `DisallowedNextLinks` only when Task 6 reaches XML compatibility.
- `src/main/java/com/citymodeler/matsim/models/io/AttributesDeserializer.java`: parse `DisallowedNextLinks` class-hinted values only when Task 6 reaches XML compatibility.
- `src/test/java/com/citymodeler/matsim/models/io/NoMatsimRuntimeImportsTest.java`: run unchanged after every task; modify only if it fails to scan newly created source directories.

Test fixtures:

- `src/test/resources/osm/minimal-network.osm`: two-way road with three nodes.
- `src/test/resources/osm/access-oneway-lanes.osm`: access, oneway, maxspeed, lanes, bus lanes.
- `src/test/resources/osm/stops-and-relations.osm`: bus stop, platform, stop position, route relation.
- `src/test/resources/osm/geometry-simplification.osm`: long way with intermediate shape nodes.
- `src/test/resources/osm/turn-restriction.osm`: `type=restriction` relation with from/via/to.
- `src/test/resources/osm/disconnected-components.osm`: main component plus small disconnected component.

---

### Task 1: Phase 1A OSM License, Provenance, And Import Contract

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/OsmElementType.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/OsmElementId.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/OsmTagSet.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/OsmIssueSeverity.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/OsmImportIssue.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/OsmProvenance.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/OsmImportConfig.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/OsmImportResult.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/model/OsmNodeRecord.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/model/OsmWayRecord.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/model/OsmRelationRecord.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/model/OsmRelationMemberRecord.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/OsmImportContractTest.java`

**Interfaces:**
- Produces: `OsmImportConfig.of(Path osmFile, String targetCrs)`
- Produces: `OsmImportConfig` getters `osmFile()`, `targetCrs()`, `keepRawTags()`, `writeProvenanceAttributes()`, `sourceName()`, `sourceLicense()`, `attributionText()`
- Produces: `OsmImportResult` getters `nodes()`, `ways()`, `relations()`, `issues()`, `provenance()`
- Produces: `OsmImportResult.applyProvenanceTo(Network network)`
- Produces: immutable records under `com.citymodeler.matsim.models.osm.model`
- Later tasks consume every type above.

- [ ] **Step 1: Write failing contract/provenance tests**

Add `OsmImportContractTest` with these tests:

```java
package com.citymodeler.matsim.models.osm;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

final class OsmImportContractTest {
    @Test
    void defaultConfigCarriesOsmAttribution() {
        OsmImportConfig config = OsmImportConfig.of(Path.of("whatever.osm"), "EPSG:3857");

        assertEquals("EPSG:3857", config.targetCrs());
        assertTrue(config.keepRawTags());
        assertTrue(config.writeProvenanceAttributes());
        assertEquals("OpenStreetMap", config.sourceName());
        assertEquals("ODbL-1.0", config.sourceLicense());
        assertEquals("© OpenStreetMap contributors", config.attributionText());
    }

    @Test
    void importResultAppliesProvenanceToNetworkAttributes() {
        OsmProvenance provenance = new OsmProvenance(
                "OpenStreetMap",
                "ODbL-1.0",
                "© OpenStreetMap contributors",
                "minimal-network.osm",
                "EPSG:3857",
                "PRESERVE_AS_LINK_GEOMETRY",
                true,
                Instant.parse("2026-09-08T00:00:00Z"));
        OsmImportResult result = new OsmImportResult(
                Map.of("1", new OsmNodeRecord("1", 1.0, 2.0, new Coord(10.0, 20.0), OsmTagSet.empty())),
                Map.of(),
                Map.of(),
                List.of(),
                provenance);

        Network network = new Network("osm-network");
        result.applyProvenanceTo(network);

        assertEquals("OpenStreetMap", network.getAttributes().getAttribute("osm:source"));
        assertEquals("ODbL-1.0", network.getAttributes().getAttribute("osm:license"));
        assertEquals("© OpenStreetMap contributors", network.getAttributes().getAttribute("osm:attribution"));
        assertEquals("minimal-network.osm", network.getAttributes().getAttribute("osm:input"));
        assertEquals("EPSG:3857", network.getAttributes().getAttribute("osm:targetCrs"));
    }

    @Test
    void resultCollectionsAreImmutable() {
        OsmImportResult result = new OsmImportResult(Map.of(), Map.of(), Map.of(), List.of(), OsmProvenance.defaultFor("input.osm", "EPSG:3857"));

        assertThrows(UnsupportedOperationException.class, () -> result.nodes().put("x", null));
        assertThrows(UnsupportedOperationException.class, () -> result.ways().put("x", new OsmWayRecord("x", List.of(), OsmTagSet.empty())));
        assertThrows(UnsupportedOperationException.class, () -> result.relations().put("x", new OsmRelationRecord("x", List.of(), OsmTagSet.empty())));
    }
}
```

- [ ] **Step 2: Run the failing test**

Run: `mvn -Dtest=OsmImportContractTest test`

Expected: compile failure because OSM contract classes do not exist.

- [ ] **Step 3: Implement immutable contract classes**

Implement:

```java
public enum OsmElementType { NODE, WAY, RELATION }
public enum OsmIssueSeverity { INFO, WARNING, ERROR }
```

Implement records:

```java
public record OsmElementId(OsmElementType type, String id) {
    public OsmElementId {
        type = Objects.requireNonNull(type, "type");
        id = Objects.requireNonNull(id, "id");
    }
}

public record OsmImportIssue(OsmIssueSeverity severity, String code, String message, OsmElementId elementId) {
    public OsmImportIssue {
        severity = Objects.requireNonNull(severity, "severity");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
    }
}

public record OsmProvenance(String sourceName, String sourceLicense, String attributionText, String inputName, String targetCrs, String geometryMode, boolean rawTagsKept, Instant importedAt) {
    public OsmProvenance {
        sourceName = Objects.requireNonNull(sourceName, "sourceName");
        sourceLicense = Objects.requireNonNull(sourceLicense, "sourceLicense");
        attributionText = Objects.requireNonNull(attributionText, "attributionText");
        inputName = Objects.requireNonNull(inputName, "inputName");
        targetCrs = Objects.requireNonNull(targetCrs, "targetCrs");
        geometryMode = Objects.requireNonNull(geometryMode, "geometryMode");
        importedAt = Objects.requireNonNull(importedAt, "importedAt");
    }
    public static OsmProvenance defaultFor(String inputName, String targetCrs) {
        return new OsmProvenance("OpenStreetMap", "ODbL-1.0", "© OpenStreetMap contributors", inputName, targetCrs, "PRESERVE_AS_LINK_GEOMETRY", true, Instant.EPOCH);
    }
}

`defaultFor` uses `Instant.EPOCH` so two runs over the same input produce byte-identical XML. Production callers pass the real import timestamp via the full constructor; `OsmImportConfig` gains an optional `java.time.Clock` field (nullable, default null) that the importer uses when constructing provenance.

public record OsmNodeRecord(String id, double lon, double lat, Coord projectedCoord, OsmTagSet tags) {
    public OsmNodeRecord {
        id = Objects.requireNonNull(id, "id");
        projectedCoord = Objects.requireNonNull(projectedCoord, "projectedCoord");
        tags = Objects.requireNonNull(tags, "tags");
    }
}

public record OsmWayRecord(String id, List<String> nodeRefs, OsmTagSet tags) {
    public OsmWayRecord {
        id = Objects.requireNonNull(id, "id");
        nodeRefs = List.copyOf(nodeRefs);
        tags = Objects.requireNonNull(tags, "tags");
    }
}

public record OsmRelationMemberRecord(OsmElementType type, String ref, String role) {
    public OsmRelationMemberRecord {
        type = Objects.requireNonNull(type, "type");
        ref = Objects.requireNonNull(ref, "ref");
        role = role == null ? "" : role;
    }
}

public record OsmRelationRecord(String id, List<OsmRelationMemberRecord> members, OsmTagSet tags) {
    public OsmRelationRecord {
        id = Objects.requireNonNull(id, "id");
        members = List.copyOf(members);
        tags = Objects.requireNonNull(tags, "tags");
    }
}
```

Implement `OsmTagSet` as a final class that defensively copies into `Collections.unmodifiableSortedMap(new TreeMap<>(tags))` — `Map.copyOf` has hash-based iteration order and would violate the determinism constraint. Exposes:

```java
public static OsmTagSet empty()
public static OsmTagSet of(Map<String, String> tags)
public String get(String key)
public boolean has(String key)
public boolean has(String key, String value)
public Map<String, String> asMap()
```

Implement `OsmImportConfig` as a record with compact constructor defaults:

```java
public record OsmImportConfig(
        Path osmFile,
        String targetCrs,
        boolean keepRawTags,
        boolean writeProvenanceAttributes,
        String sourceName,
        String sourceLicense,
        String attributionText) {
    public static OsmImportConfig of(Path osmFile, String targetCrs) {
        return new OsmImportConfig(
                osmFile,
                targetCrs,
                true,
                true,
                "OpenStreetMap",
                "ODbL-1.0",
                "© OpenStreetMap contributors");
    }
}
```

Implement `OsmImportResult` with immutable, deterministically-ordered copies (`Collections.unmodifiableSortedMap(new TreeMap<>(...))` for maps) and:

```java
public void applyProvenanceTo(Network network) {
    network.getAttributes().putAttribute("osm:source", provenance.sourceName());
    network.getAttributes().putAttribute("osm:license", provenance.sourceLicense());
    network.getAttributes().putAttribute("osm:attribution", provenance.attributionText());
    network.getAttributes().putAttribute("osm:input", provenance.inputName());
    network.getAttributes().putAttribute("osm:targetCrs", provenance.targetCrs());
    network.getAttributes().putAttribute("osm:geometryMode", provenance.geometryMode());
    network.getAttributes().putAttribute("osm:rawTagsKept", provenance.rawTagsKept());
    network.getAttributes().putAttribute("osm:importedAt", provenance.importedAt().toString());
}
```

- [ ] **Step 4: Run contract tests**

Run: `mvn -Dtest=OsmImportContractTest test`

Expected: PASS.

- [ ] **Step 5: Run import guard**

Run: `mvn -Dtest=NoMatsimRuntimeImportsTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/java/com/citymodeler/matsim/models/osm src/test/java/com/citymodeler/matsim/models/osm/OsmImportContractTest.java
git commit -m "feat: add OSM import contract and provenance"
```

---

### Task 2: Phase 1B OSM XML Parsing And Tag Retention

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/OsmNetworkImporter.java`
- Create fixtures: `src/test/resources/osm/minimal-network.osm`, `src/test/resources/osm/stops-and-relations.osm`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/OsmNetworkImporterTest.java`

**Interfaces:**
- Consumes: Task 1 `OsmImportConfig`, `OsmImportResult`, source records.
- Produces: `public OsmImportResult read(OsmImportConfig config)`.
- Produces: parser support for OSM `node`, `way`, `relation`, `nd`, `member`, and `tag` elements.

- [ ] **Step 1: Add synthetic OSM fixtures**

Create `minimal-network.osm`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="matsim-models-test">
  <node id="1" lat="0.000000" lon="0.000000" />
  <node id="2" lat="0.000000" lon="0.001000" />
  <node id="3" lat="0.000000" lon="0.002000" />
  <way id="10">
    <nd ref="1" />
    <nd ref="2" />
    <nd ref="3" />
    <tag k="highway" v="primary" />
    <tag k="name" v="Main Street" />
    <tag k="maxspeed" v="50" />
  </way>
</osm>
```

Create `stops-and-relations.osm`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="matsim-models-test">
  <node id="100" lat="0.000000" lon="0.000000"><tag k="highway" v="bus_stop" /><tag k="name" v="Central" /></node>
  <node id="101" lat="0.000000" lon="0.001000"><tag k="public_transport" v="stop_position" /><tag k="bus" v="yes" /></node>
  <node id="102" lat="0.000000" lon="0.002000" />
  <way id="200"><nd ref="100" /><nd ref="101" /><nd ref="102" /><tag k="highway" v="busway" /><tag k="bus" v="designated" /></way>
  <relation id="300">
    <member type="way" ref="200" role="" />
    <member type="node" ref="100" role="platform" />
    <member type="node" ref="101" role="stop" />
    <tag k="type" v="route" />
    <tag k="route" v="bus" />
    <tag k="ref" v="B1" />
  </relation>
</osm>
```

- [ ] **Step 2: Write failing parser tests**

Add tests asserting:

```java
OsmImportResult result = new OsmNetworkImporter().read(OsmImportConfig.of(Path.of("src/test/resources/osm/minimal-network.osm"), "EPSG:3857"));
assertEquals(3, result.nodes().size());
assertEquals(1, result.ways().size());
assertEquals(List.of("1", "2", "3"), result.ways().get("10").nodeRefs());
assertEquals("primary", result.ways().get("10").tags().get("highway"));
assertNotEquals(0.0, result.nodes().get("2").projectedCoord().getX());
```

Add tests for `stops-and-relations.osm` asserting relation id `300` has three members and tags `type=route`, `route=bus`, `ref=B1`.

Add a config test with `keepRawTags=false` asserting parsed node/way/relation `tags().asMap()` is empty.

- [ ] **Step 3: Run failing parser tests**

Run: `mvn -Dtest=OsmNetworkImporterTest test`

Expected: compile failure because `OsmNetworkImporter` does not exist.

- [ ] **Step 4: Implement `OsmNetworkImporter`**

Use StAX with hardened XML settings, following the defensive style in `facilities/osm/OsmFacilityParser`:

```java
XMLInputFactory factory = XMLInputFactory.newInstance();
factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
```

Use proj4j exactly like `OsmFacilityConverter`: source CRS `EPSG:4326`, target CRS from config.

Implementation rules:

- Read in one streaming pass because OSM files normally emit nodes before ways, but records can be stored as source records for v1.
- Store maps as `LinkedHashMap` while parsing, then immutable, sorted copies in `OsmImportResult`.
- Open the stream through the existing gzip-aware IO helper so `.osm.gz` inputs work identically; add one parity test with a gzipped copy of `minimal-network.osm`.
- On `node`, parse `id`, `lat`, `lon`, child tags, and projected coordinate.
- On `way`, parse ordered `nd ref`s and child tags.
- On `relation`, parse ordered members (with role) and child tags.
- Convert member type strings `node`, `way`, `relation` into `OsmElementType`.
- `keepRawTags` is an output-retention switch, not a parse switch: tags are ALWAYS parsed into records (the builder and resolvers need them). With `keepRawTags=false`, the importer records `rawTagsKept=false` in provenance and downstream link-attribute writing skips `osm:tag:<key>` attributes.
- Wrap XML/projection failures in `MatsimParseException` with message prefix `Failed to parse OSM file`.

- [ ] **Step 5: Run parser tests**

Run: `mvn -Dtest=OsmNetworkImporterTest,OsmImportContractTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/java/com/citymodeler/matsim/models/osm src/test/java/com/citymodeler/matsim/models/osm src/test/resources/osm
git commit -m "feat: parse OSM XML source records"
```

---

### Task 3: Phase 1C OSM Way Rules, Access, Speeds, And Directed Links

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmWayRule.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkBuildConfig.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkBuildResult.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmMatsimNetworkBuilder.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmModeAccessResolver.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmSpeedResolver.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneResolver.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmGeneratedIds.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLinkRef.java`
- Create fixture: `src/test/resources/osm/access-oneway-lanes.osm`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmMatsimNetworkBuilderTest.java`

**Interfaces:**
- Consumes: Task 2 `OsmImportResult`.
- Produces: `OsmMatsimNetworkBuilder.build(OsmImportResult, OsmNetworkBuildConfig)`.
- Produces: `OsmNetworkBuildResult.cleanedNetwork()`, `issues()`, `linkRefsByLinkId()`, `linkIdsByOsmWayId()`.
- Produces: `OsmLaneResolver.resolve(OsmWayRecord way, OsmWayRule rule, boolean forward, boolean oneway)` (public; Task 4's hint extractor calls the same method).
- Later tasks add geometry/hints/indexes to `OsmNetworkBuildResult` without changing its core network API.

- [ ] **Step 1: Add synthetic access/lanes fixture**

Create `access-oneway-lanes.osm`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="matsim-models-test">
  <node id="1" lat="0.000000" lon="0.000000" />
  <node id="2" lat="0.000000" lon="0.001000" />
  <node id="3" lat="0.001000" lon="0.001000" />
  <way id="10">
    <nd ref="1" /><nd ref="2" />
    <tag k="highway" v="primary" />
    <tag k="maxspeed" v="35 mph" />
    <tag k="lanes" v="4" />
  </way>
  <way id="11">
    <nd ref="2" /><nd ref="3" />
    <tag k="highway" v="busway" />
    <tag k="oneway" v="yes" />
    <tag k="bus" v="designated" />
    <tag k="access" v="no" />
    <tag k="psv" v="yes" />
    <tag k="lanes" v="1" />
  </way>
</osm>
```

- [ ] **Step 2: Write failing builder tests**

Task 3 runs before the geometry simplifier exists (Task 5), so all Task 3 tests pin `OsmNetworkBuildConfig` to `MATERIALIZE_GEOMETRY_NODES` (every OSM node becomes a network node; one link per consecutive node pair per direction). Task 5 will update these expectations when compact modes land.

Test `minimal-network.osm` (way `10`: nodes 1→2→3, bidirectional, MATERIALIZE mode) produces **four** directed links — 2 node segments × 2 directions: `osm_way_10_0_f`, `osm_way_10_1_f`, `osm_way_10_0_r`, `osm_way_10_1_r` — each with source OSM id attribute `osm:wayId=10`, type attribute `osm:highway=primary`, mode `car`, and length greater than zero.

Test `access-oneway-lanes.osm` (MATERIALIZE mode) produces:

- way `10` (nodes 1→2, one segment, bidirectional): **two** directed car links (`osm_way_10_0_f`, `osm_way_10_0_r`) with `permlanes=2.0` each, freespeed approximately `15.6464` m/s for `35 mph`.
- way `11` (nodes 2→3, oneway=yes): one forward directed link (`osm_way_11_0_f`), allowed modes contain `bus` and `pt`, and no reverse link.

Separate assertion blocks per fixture:

```java
// minimal-network.osm
assertEquals(3, network.getNodes().size());
assertEquals(4, network.getLinks().size());
assertEquals(2, result.linkIdsByOsmWayId().get("10").stream().filter(id -> id.endsWith("_f")).count());

// access-oneway-lanes.osm
assertEquals(3, network.getNodes().size());
assertEquals(3, network.getLinks().size()); // 2 from way 10, 1 from way 11
assertEquals(1, result.linkIdsByOsmWayId().get("10").size());
assertEquals(1, result.linkIdsByOsmWayId().get("11").size());
```

- [ ] **Step 3: Run failing builder tests**

Run: `mvn -Dtest=OsmMatsimNetworkBuilderTest test`

Expected: compile failure because network builder classes do not exist.

- [ ] **Step 4: Implement rules and resolvers**

Implement `OsmWayRule` as a record:

```java
public record OsmWayRule(String key, String value, int hierarchy, Set<String> allowedModes, double lanesPerDirection, double freespeedMetersPerSecond, double capacityPerLane, boolean defaultOneway, boolean transitRelevant) {
    public OsmWayRule {
        key = Objects.requireNonNull(key, "key");
        value = Objects.requireNonNull(value, "value");
        allowedModes = Set.copyOf(allowedModes);
        if (lanesPerDirection <= 0.0) throw new IllegalArgumentException("lanesPerDirection must be positive");
        if (freespeedMetersPerSecond <= 0.0) throw new IllegalArgumentException("freespeedMetersPerSecond must be positive");
        if (capacityPerLane <= 0.0) throw new IllegalArgumentException("capacityPerLane must be positive");
    }
}
```

Implement `OsmNetworkBuildConfig` with two named factories:

- `defaultConfig()`: geometry mode `PRESERVE_AS_LINK_GEOMETRY` (used from Task 5 onward)
- `materializeGeometryConfig()`: geometry mode `MATERIALIZE_GEOMETRY_NODES` (used by Task 3/4 tests before simplification exists)

with deterministic `LinkedHashMap` rules for:

- `highway=motorway`, `trunk`, `primary`, `secondary`, `tertiary`, `unclassified`, `residential`, `living_street`, `service`
- link variants for motorway/trunk/primary/secondary/tertiary
- `highway=busway`
- `railway=rail`, `light_rail`, `subway`, `tram`, `monorail`, `funicular`
- `route=ferry` ways with mode `ferry` plus configured passenger modes

Default modes:

- normal highway: `car`
- busway: `bus`, `pt`
- railway/tram/subway/funicular: matching rail mode plus `pt`
- ferry: `ferry` plus configured passenger modes

Implement `OsmModeAccessResolver` (hierarchical, per the spec's Access Resolution):

- resolve per mode through the hierarchy `access` → `vehicle` → `motor_vehicle` → `psv` → `bus`, most specific key wins; directional `*:forward`/`:backward` overrides apply after
- values map to three states: `FORBIDDEN` (`no`, `never`), `LEGALLY_RESTRICTED` (`private`, `destination`, `customers`, `delivery`), `ALLOWED` (`yes`, `designated`, `permissive`)
- `access=no` forbids all modes unless mode-specific tags re-enable them
- `bus=yes/designated`, `psv=yes/designated` allow `bus` and `pt`
- `motor_vehicle=no` removes `car`
- presence of any `*:conditional` access tag produces a warning and forces the conservative (forbidden) state for that mode

Implement `OsmOnewayResolver` behavior inside `OsmModeAccessResolver`:

- `oneway=yes/true/1` means forward only; `-1`/`reverse` means backward only; `no/false/0` bidirectional; `alternating`/`reversible` treated as forward-only with a warning
- implied oneways after explicit tags: `junction=roundabout` and `highway=motorway` default forward-oneway unless `oneway=no`
- mode exceptions last: `oneway:bus=no` on an `oneway=yes` way produces a reverse bus-only link (only when resolved bus access allows that direction)

Implement `OsmSpeedResolver`:

- `maxspeed:forward` or `maxspeed:backward` wins for that direction.
- `maxspeed` wins next.
- numeric value means km/h and converts using `/ 3.6`.
- `mph` values convert using `mph * 1.609344 / 3.6`.
- `none` and `walk` map to configured finite speeds (`maxspeedNoneKph`, `maxspeedWalkKph`).
- unparseable or unknown-symbol values produce one warning per way and fall back to rule default.

Implement `OsmLaneResolver` with public method `resolve(OsmWayRecord way, OsmWayRule rule, boolean forward, boolean oneway)` (used by the builder and by Task 4's hint extractor):

- `lanes:forward` / `lanes:backward` win for that direction.
- if exactly one direction is tagged and total `lanes` is present, derive the missing direction as `lanes − tagged − both_ways`.
- `lanes:both_ways` adds to each direction's approach count.
- bidirectional `lanes` splits evenly; odd remainder goes forward with a warning.
- `forward + backward + both_ways != lanes` with all present: conflict warning, use tagged directional values.
- missing values use rule `lanesPerDirection`.

- [ ] **Step 5: Implement directed network builder**

Builder algorithm for Task 3 only:

- Create one `Network` named `osm-network`.
- Add all source nodes from `OsmImportResult` as network nodes with ids `osm_node_<id>`.
- For each source way with a matching `OsmWayRule`, create directed links between each consecutive pair of node refs.
- Link ids use `OsmGeneratedIds.linkId(wayId, segmentIndex, forward)` returning `osm_way_<wayId>_<segmentIndex>_f` or `_r`.
- Link length is Euclidean distance between projected node coordinates.
- Capacity is `lanes * capacityPerLane`.
- Set link attributes `osm:wayId`, `osm:key`, `osm:value`, plus raw way tags if `keepRawTags=true` using `osm:tag:<key>` keys.
- Populate `OsmLinkRef(linkId, wayId, segmentIndex, forward)` sidecar entries.
- Call `network.postProcess()` before returning.
- Apply provenance from `OsmImportResult` to the network.

- [ ] **Step 6: Run builder tests**

Run: `mvn -Dtest=OsmMatsimNetworkBuilderTest,OsmNetworkImporterTest,OsmImportContractTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add src/main/java/com/citymodeler/matsim/models/osm/network src/test/java/com/citymodeler/matsim/models/osm/network src/test/resources/osm/access-oneway-lanes.osm
git commit -m "feat: build directed networks from OSM ways"
```

---

### Task 4: Phase 1D Lane, Intersection, Stop, And Relation Hints

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmStopKind.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmStopHint.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneHint.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmIntersectionLaneHint.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmStopHintExtractor.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmLaneHintExtractor.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkBuildResult.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmMatsimNetworkBuilder.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmHintExtractorTest.java`

**Interfaces:**
- Consumes: Task 3 `OsmNetworkBuildResult`, source records, link refs.
- Produces: `OsmNetworkBuildResult.stopHints()`, `laneHintsByLinkId()`, `intersectionLaneHintsByNodeId()`.
- Later index task consumes these maps.

- [ ] **Step 1: Write failing hint tests**

Use `stops-and-relations.osm` to assert:

```java
assertEquals(2, result.stopHints().size());
assertTrue(result.stopHints().stream().anyMatch(h -> h.kind() == OsmStopKind.BUS_STOP && "Central".equals(h.name())));
assertTrue(result.stopHints().stream().anyMatch(h -> h.kind() == OsmStopKind.STOP_POSITION && h.servedModes().contains("bus")));
```

Use `access-oneway-lanes.osm` to assert:

```java
OsmLaneHint hint = result.laneHintsByLinkId().values().stream()
        .filter(h -> h.osmWayId().equals("11"))
        .findFirst()
        .orElseThrow();
assertEquals(1.0, hint.totalLanes());
assertTrue(hint.dedicatedTransitLane());
assertTrue(hint.servedModes().contains("bus"));
```

Add an intersection assertion for node `osm_node_2` having incoming and outgoing links.

- [ ] **Step 2: Run failing hint tests**

Run: `mvn -Dtest=OsmHintExtractorTest test`

Expected: compile failure because hint classes do not exist.

- [ ] **Step 3: Implement hint value objects**

Implement records:

```java
public enum OsmStopKind { BUS_STOP, PLATFORM, STOP_POSITION, RAILWAY_STATION, RAILWAY_HALT, TRAM_STOP, BUS_STATION, FERRY_TERMINAL }
public record OsmStopHint(String osmId, OsmElementType elementType, OsmStopKind kind, Coord coord, String name, String ref, String operator, String network, Set<String> servedModes, List<String> parentRelationIds, List<String> nearbyLinkIds) {
    public OsmStopHint {
        osmId = Objects.requireNonNull(osmId, "osmId");
        elementType = Objects.requireNonNull(elementType, "elementType");
        kind = Objects.requireNonNull(kind, "kind");
        coord = Objects.requireNonNull(coord, "coord");
        servedModes = Set.copyOf(servedModes);
        parentRelationIds = List.copyOf(parentRelationIds);
        nearbyLinkIds = List.copyOf(nearbyLinkIds);
    }
}

public record OsmLaneHint(String linkId, String osmWayId, boolean forward, double totalLanes, double busLanes, double psvLanes, String turnLanes, boolean dedicatedTransitLane, Set<String> servedModes) {
    public OsmLaneHint {
        linkId = Objects.requireNonNull(linkId, "linkId");
        osmWayId = Objects.requireNonNull(osmWayId, "osmWayId");
        servedModes = Set.copyOf(servedModes);
    }
}

public record OsmIntersectionLaneHint(String nodeId, List<String> incomingLinkIds, List<String> outgoingLinkIds, Map<String, Double> approachLanesByIncomingLinkId, Map<String, String> turnLanesByIncomingLinkId, boolean trafficSignal) {
    public OsmIntersectionLaneHint {
        nodeId = Objects.requireNonNull(nodeId, "nodeId");
        incomingLinkIds = List.copyOf(incomingLinkIds);
        outgoingLinkIds = List.copyOf(outgoingLinkIds);
        approachLanesByIncomingLinkId = Map.copyOf(approachLanesByIncomingLinkId);
        turnLanesByIncomingLinkId = Map.copyOf(turnLanesByIncomingLinkId);
    }
}
```

Use immutable copies in compact constructors.

- [ ] **Step 4: Implement stop extraction**

`OsmStopHintExtractor.extract(OsmImportResult, OsmNetworkBuildResult)` rules:

- Node with `highway=bus_stop` -> `BUS_STOP`, served mode `bus`.
- Node/way with `public_transport=stop_position` -> `STOP_POSITION`, served mode from `bus`, `tram`, `train`, `subway`, `ferry` tags.
- Node/way with `public_transport=platform` -> `PLATFORM`.
- Node/way with `railway=station` -> `RAILWAY_STATION`.
- Node/way with `railway=halt` -> `RAILWAY_HALT`.
- Node/way with `railway=tram_stop` -> `TRAM_STOP`, served mode `tram`.
- Node/way with `amenity=bus_station` -> `BUS_STATION`, served mode `bus`.
- Use node coordinate directly for nodes.
- For ways, use average of member projected coordinates for v1.
- Add route/route_master relation ids when relation members reference the stop element.

- [ ] **Step 5: Implement lane and intersection extraction**

`OsmLaneHintExtractor.extractLaneHints(OsmImportResult importResult, OsmNetworkBuildResult partialBuildResult)` rules:

- Reuse parsed lane values from `OsmLaneResolver` by calling the same public method used by the network builder: `resolve(OsmWayRecord way, OsmWayRule rule, boolean forward, boolean oneway)`.
- Parse `lanes:bus`, `lanes:psv` numeric values (including directional variants).
- Parse `bus:lanes` / `psv:lanes` pipe-delimited strings and count entries equal to `designated`.
- Preserve `turn:lanes`, `turn:lanes:forward`, `turn:lanes:backward` string for the matching direction.
- `dedicatedTransitLane=true` if bus/psv lane count is greater than zero, a lane pattern contains `designated`, or the source way has way-level `bus=designated|yes` / `psv=designated|yes` with resolved bus access — in the way-level case set `busLanes = totalLanes` for the bus-legal direction.
- Determine `servedModes` from the resolved access profile; a way with `bus=designated` but no general car access contributes mode `bus` (plus `pt`).

`extractIntersectionLaneHints(OsmImportResult importResult, Network network, Map<String, OsmLaneHint> laneHints)` rules (takes `OsmImportResult` because source node tags are needed):

- For each network node, use `getInLinks()` and `getOutLinks()`.
- Map each incoming link id to its lane count.
- Map each incoming link id to its turn-lane string when present.
- `trafficSignal=true` if source OSM node had `highway=traffic_signals`.

- [ ] **Step 6: Wire hints into build result**

Add hint fields to `OsmNetworkBuildResult` and populate them from `OsmMatsimNetworkBuilder` after directed links are created.

- [ ] **Step 7: Run hint tests**

Run: `mvn -Dtest=OsmHintExtractorTest,OsmMatsimNetworkBuilderTest test`

Expected: PASS.

- [ ] **Step 8: Commit**

Run:

```bash
git add src/main/java/com/citymodeler/matsim/models/osm/network src/test/java/com/citymodeler/matsim/models/osm/network/OsmHintExtractorTest.java
git commit -m "feat: extract OSM transit and lane hints"
```

---

### Task 5: Phase 1E Geometry Preservation And Routing Node Simplification

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmGeometryMode.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmPolyline.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmGeometryStore.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkSimplifier.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkBuildConfig.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmMatsimNetworkBuilder.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkBuildResult.java`
- Create fixture: `src/test/resources/osm/geometry-simplification.osm`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmGeometrySimplificationTest.java`

**Interfaces:**
- Consumes: Task 3 directed link creation and Task 4 stop hints.
- Produces: `OsmNetworkBuildConfig.geometryMode()` and `preserveTransitStopNodes()`.
- Produces: `OsmNetworkBuildResult.geometryStore()`.
- Later spatial indexes use `OsmGeometryStore` for point-to-segment distances.

- [ ] **Step 1: Add geometry fixture**

The fixture must contain a shape-only interior node (referenced by exactly one converted way), otherwise no geometry mode is distinguishable. Node `7` below is the shape-only node (way `13`: 6→7→8).

Create `geometry-simplification.osm`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="matsim-models-test">
  <node id="1" lat="0.000000" lon="0.000000" />
  <node id="2" lat="0.000500" lon="0.000500" />
  <node id="3" lat="0.001000" lon="0.001000" />
  <node id="4" lat="0.001000" lon="0.002000" />
  <node id="5" lat="0.000000" lon="0.001000" />
  <node id="6" lat="0.002000" lon="0.000000" />
  <node id="7" lat="0.002500" lon="0.000500" />
  <node id="8" lat="0.003000" lon="0.001000" />
  <way id="10"><nd ref="1" /><nd ref="2" /><nd ref="3" /><tag k="highway" v="primary" /></way>
  <way id="11"><nd ref="3" /><nd ref="4" /><tag k="highway" v="primary" /></way>
  <way id="12"><nd ref="5" /><nd ref="2" /><tag k="highway" v="service" /></way>
  <way id="13"><nd ref="6" /><nd ref="7" /><nd ref="8" /><tag k="highway" v="residential" /></way>
</osm>
```

- [ ] **Step 2: Write failing geometry tests**

Test default `PRESERVE_AS_LINK_GEOMETRY`:

- Routing nodes: nodes 1, 2, 3, 4, 5, 6, 8 retained (endpoints/intersections); shape-only node `7` NOT materialized as a network node.
- Way `13` becomes one forward and one reverse link between `osm_node_6` and `osm_node_8`, each with a 3-point polyline (6→7→8), and `getLength()` equals `|6→7| + |7→8|` (sum of segment lengths, not the endpoint chord).
- Geometry store contains polyline points for each generated link.

Test `MATERIALIZE_GEOMETRY_NODES`:

- Every OSM node referenced by converted ways, including node `7`, appears as a network node (8 nodes).
- Link count is strictly greater than the default mode (way 13 contributes 2 segments × 2 directions = 4 links instead of 2).

Test `ROUTING_NODES_ONLY`:

- Same compact network as default mode; geometry store contains only start/end points for each link (2-point polylines).

Test update to existing builder tests:

- `OsmMatsimNetworkBuilderTest` (pinned to `materializeGeometryConfig()` in Task 3) gains a companion case with `defaultConfig()`: for `minimal-network.osm`, way 10 compacts to 2 nodes (1, 3) and 2 links (`osm_way_10_0_f/_r`) with 3-point polylines; for `access-oneway-lanes.osm`, way 10 compacts to 1 segment × 2 directions = 2 links and way 11 stays 1 link (3 nodes, 3 links total).

- [ ] **Step 3: Run failing geometry tests**

Run: `mvn -Dtest=OsmGeometrySimplificationTest test`

Expected: compile failure because geometry mode/store/simplifier do not exist.

- [ ] **Step 4: Implement geometry value objects and config**

Implement:

```java
public enum OsmGeometryMode { ROUTING_NODES_ONLY, PRESERVE_AS_LINK_GEOMETRY, MATERIALIZE_GEOMETRY_NODES }
public record OsmPolyline(List<Coord> points) {
    public OsmPolyline {
        points = List.copyOf(points);
        if (points.size() < 2) throw new IllegalArgumentException("Polyline requires at least two points");
    }
}
public final class OsmGeometryStore { public OsmPolyline geometryForLink(String linkId); public Map<String, OsmPolyline> asMap(); }
```

Add to `OsmNetworkBuildConfig`:

```java
OsmGeometryMode geometryMode
boolean preserveTransitStopNodes
Set<String> explicitOsmNodeIdsToKeep
double sharpBendAngleDegrees
```

Default config values:

- `geometryMode=PRESERVE_AS_LINK_GEOMETRY`
- `preserveTransitStopNodes=true`
- empty explicit node id set
- `sharpBendAngleDegrees=35.0`

- [ ] **Step 5: Implement simplifier**

`OsmNetworkSimplifier` should compute retained OSM node ids before link creation:

- Always keep first and last node of each converted way.
- Keep nodes referenced by more than one converted way.
- Keep nodes explicitly configured.
- Keep stop-position/platform nodes when `preserveTransitStopNodes=true`.
- Keep nodes involved in restriction relations; Task 6 will expand this.
- Keep all way nodes when mode is `MATERIALIZE_GEOMETRY_NODES`.

Link creation changes:

- For `MATERIALIZE_GEOMETRY_NODES`, create links between every consecutive OSM node ref.
- For compact modes, create links between retained routing nodes, with intermediate shape points stored in `OsmGeometryStore`.
- Length uses full polyline length, not straight-line endpoint distance, when sidecar geometry is available.
- `ROUTING_NODES_ONLY` creates same compact links but stores only start/end points.

- [ ] **Step 6: Run geometry tests**

Run: `mvn -Dtest=OsmGeometrySimplificationTest,OsmMatsimNetworkBuilderTest,OsmHintExtractorTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add src/main/java/com/citymodeler/matsim/models/osm/network src/test/java/com/citymodeler/matsim/models/osm/network/OsmGeometrySimplificationTest.java src/test/resources/osm/geometry-simplification.osm
git commit -m "feat: preserve OSM geometry sidecars"
```

---

### Task 6: Phase 1F Turn Restrictions, Cleaning, And Query Indexes

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/network/turnrestrictions/DisallowedNextLinks.java`
- Create: `src/main/java/com/citymodeler/matsim/models/network/turnrestrictions/TurnRestrictionIndex.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmTurnRestrictionReader.java`
- Create: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkCleaner.java`
- Create: `src/main/java/com/citymodeler/matsim/models/network/index/NetworkQueryIndex.java`
- Create: `src/main/java/com/citymodeler/matsim/models/network/index/NetworkQueryIndexBuilder.java`
- Create: `src/main/java/com/citymodeler/matsim/models/network/index/LinkSpatialIndex.java`
- Create: `src/main/java/com/citymodeler/matsim/models/network/index/NodeSpatialIndex.java`
- Create: `src/main/java/com/citymodeler/matsim/models/network/index/StopHintIndex.java`
- Create: `src/main/java/com/citymodeler/matsim/models/network/index/LaneHintIndex.java`
- Create: `src/main/java/com/citymodeler/matsim/models/network/index/NearestLink.java`
- Create: `src/main/java/com/citymodeler/matsim/models/network/index/NearestNode.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/AttributesSerializer.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/AttributesDeserializer.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/osm/network/OsmNetworkBuildResult.java`
- Create fixtures: `src/test/resources/osm/turn-restriction.osm`, `src/test/resources/osm/disconnected-components.osm`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmTurnRestrictionTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/osm/network/OsmNetworkCleanerTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/network/index/NetworkQueryIndexTest.java`

**Interfaces:**
- Consumes: Tasks 1-5 complete Phase 1 source/build objects.
- Produces: final `OsmNetworkBuildResult.cleanedNetwork()`, `queryIndex()`, `turnRestrictionIndex()`.
- Produces: bounded nearest-link/nearest-node/nearest-stop queries for Phase 2.

- [ ] **Step 1: Add turn restriction and disconnected fixtures**

Create `turn-restriction.osm` with three ways meeting at via node `2` and a no-right-turn relation:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="matsim-models-test">
  <node id="1" lat="0.000000" lon="0.000000" />
  <node id="2" lat="0.000000" lon="0.001000" />
  <node id="3" lat="0.001000" lon="0.001000" />
  <node id="4" lat="0.000000" lon="0.002000" />
  <way id="10"><nd ref="1" /><nd ref="2" /><tag k="highway" v="primary" /></way>
  <way id="11"><nd ref="2" /><nd ref="3" /><tag k="highway" v="primary" /></way>
  <way id="12"><nd ref="2" /><nd ref="4" /><tag k="highway" v="primary" /></way>
  <relation id="1000">
    <member type="way" ref="10" role="from" />
    <member type="node" ref="2" role="via" />
    <member type="way" ref="11" role="to" />
    <tag k="type" v="restriction" />
    <tag k="restriction" v="no_right_turn" />
  </relation>
</osm>
```

Create `disconnected-components.osm` with one primary component and one isolated transit-relevant component:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="matsim-models-test">
  <node id="1" lat="0.000000" lon="0.000000" />
  <node id="2" lat="0.000000" lon="0.001000" />
  <node id="3" lat="0.000000" lon="0.002000" />
  <node id="10" lat="0.010000" lon="0.000000" />
  <node id="11" lat="0.010000" lon="0.001000" />
  <way id="20"><nd ref="1" /><nd ref="2" /><nd ref="3" /><tag k="highway" v="primary" /></way>
  <way id="21"><nd ref="10" /><nd ref="11" /><tag k="highway" v="busway" /><tag k="bus" v="designated" /></way>
</osm>
```

- [ ] **Step 2: Write failing turn restriction tests**

Assert:

```java
Link from = findForwardLinkForWay(result, "10");
Link to = findForwardLinkForWay(result, "11");
DisallowedNextLinks disallowed = (DisallowedNextLinks) from.getAttributes().getAttribute("disallowedNextLinks");
assertTrue(disallowed.isDisallowed("car", List.of(to.getId().toString())));
assertTrue(result.turnRestrictionIndex().isDisallowed("car", from.getId().toString(), to.getId().toString()));
```

The restriction resolves from/to by relation roles, not by generic via-node adjacency: `from` is the produced link of way `10` that ends at via node `2` in way `10`'s direction of travel (`osm_way_10_0_f`); `to` is the produced link of way `11` that starts at node `2` (`osm_way_11_0_f`). Add **negative assertions** that unrelated movements at the via node are NOT restricted:

```java
assertFalse(result.turnRestrictionIndex().isDisallowed("car", "osm_way_11_0_r", "osm_way_10_0_r"));
assertFalse(result.turnRestrictionIndex().isDisallowed("car", "osm_way_11_0_r", "osm_way_12_0_f"));
assertFalse(result.turnRestrictionIndex().isDisallowed("car", "osm_way_10_0_f", "osm_way_10_0_r"));
```

Add an **unconditional** XML round-trip test through `NetworkXmlWriter` and `NetworkXmlReader`:

```java
Network written = roundTripThroughXml(result.cleanedNetwork());
Link roundTripped = written.getLinks().get(from.getId());
DisallowedNextLinks attribute = (DisallowedNextLinks) roundTripped.getAttributes().getAttribute("disallowedNextLinks");
assertNotNull(attribute);
assertTrue(attribute.isDisallowed("car", List.of(to.getId().toString())));
```

and assert the written XML contains the exact MATSim-compatible class-hint string (see Step 6).

- [ ] **Step 3: Write failing cleaner tests**

Two cleaner scenarios using `disconnected-components.osm`:

Test A (defaults, `keepWaysWithTransit=true`): the `highway=primary` component survives as the primary component; the isolated `busway` component is **quarantined** — absent from `cleanedNetwork()` but present in `result.quarantinedComponents()` with a warning issue; it is not silently deleted.

Test B (`keepWaysWithTransit=false`): the isolated busway component is removed entirely and an `OsmImportIssue` with code `DROPPED_TRANSIT_RELEVANT_COMPONENT` carrying source OSM way id `21` exists.

Additional assertions:

```java
assertTrue(result.cleanedNetwork().getLinks().values().stream().allMatch(l -> l.getAllowedModes().contains("car")));
assertTrue(result.linkRefsByLinkId().keySet().containsAll(result.cleanedNetwork().getLinks().keySet()));
```

(sidecars reference only surviving ids).

- [ ] **Step 4: Write failing index tests**

Build from `minimal-network.osm` for nearest-link and outgoing-link assertions. Build from `stops-and-relations.osm` for nearest-stop assertions. Assert:

```java
NetworkQueryIndex index = result.queryIndex();
List<NearestLink> links = index.nearestLinks(new Coord(50.0, 0.0), Set.of("car"), 200.0, 3);
assertFalse(links.isEmpty());
assertTrue(links.get(0).distanceMeters() >= 0.0);
assertTrue(links.size() <= 3);
assertFalse(index.outgoingLinks(Id.create("osm_node_1", Node.class), "car").isEmpty());
```

- [ ] **Step 5: Run failing Phase 1F tests**

Run: `mvn -Dtest=OsmTurnRestrictionTest,OsmNetworkCleanerTest,NetworkQueryIndexTest test`

Expected: compile failures because restriction, cleaner, and index classes do not exist.

- [ ] **Step 6: Implement `DisallowedNextLinks` and XML attribute support on the DOM path**

Implement `DisallowedNextLinks` as immutable mode-to-sequences value object:

```java
public final class DisallowedNextLinks {
    public static DisallowedNextLinks empty();
    public DisallowedNextLinks plus(String mode, List<String> nextLinkSequence);
    public boolean isDisallowed(String mode, List<String> nextLinkSequence);
    public Map<String, List<List<String>>> asMap();
    public static DisallowedNextLinks fromJson(String json);
    public String toJson();
}
```

Wire contract (per spec):

- the value serializes as JSON `{"car":[["linkA","linkB"]]}` with deterministic key/list ordering
- the class hint written for `disallowedNextLinks` values is `org.matsim.core.network.turnRestrictions.DisallowedNextLinks`
- the reader accepts that hint plus the historic alias `org.matsim.core.network.DisallowedNextLinks`
- malformed values produce a warning issue and are dropped, never corrupting other attributes

**Important:** the network XML attribute path is DOM-based `XmlSupport`, not the Jackson mapper. Modify `XmlSupport`:

- `classHint(Object value)`: if `value instanceof DisallowedNextLinks`, return the MATSim-compatible class-hint string above; keep existing behavior for String/Double/Integer/Long/Boolean.
- `appendAttributes(...)`: for a `DisallowedNextLinks` value, write `value.toJson()` as the text content (not `toString()` of the object identity).
- `readAttributes(...)`/`convert(...)`: if the attribute name is `disallowedNextLinks` (or the class hint is one of the two accepted hints), return `DisallowedNextLinks.fromJson(text)`.

This introduces one `org.matsim.*` string literal in main source (the class hint constant). Add it as a single named, commented allowlist constant `MATSIM_DISALLOWED_NEXT_LINKS_CLASS_HINT` and update `NoMatsimRuntimeImportsTest`/the final-verification grep to exclude exactly that constant.

Secondary consistency: add the same hint/value mapping to Jackson `AttributesSerializer`/`AttributesDeserializer` and extend `AttributesSerdeTest`, so attribute handling is uniform across both code paths.

- [ ] **Step 7: Implement OSM turn restriction reader**

`OsmTurnRestrictionReader` rules:

- Read relations with `type=restriction` (and legacy `type=restriction:<mode>`).
- Resolve `from` and `to` **by relation roles**: `from` must be a produced link of the from-way that ends at the via location in the from-way's direction of travel; `to` must be a produced link of the to-way that starts at the via location. Never infer restrictions from generic via-node adjacency.
- Via is either one node or an ordered connected via-way chain; via-way restrictions record `UNSUPPORTED_VIA_WAY` in this task (full support is a spec'd follow-up) rather than a generic unresolved warning.
- `restriction=no_*` records one disallowed sequence from-link → to-link for the affected mode.
- `restriction=only_*` records disallowed sequences from-link → every alternative outgoing link at the via location except the to-link (and the from-link's own reverse for `only_u_turn`).
- `restriction:<mode>` keys restrict only that mode; a bare `restriction` applies to `car` by default and additionally to `bus` when both resolved links allow `bus`.
- `except=<modes>` removes listed modes from the restriction's mode set.
- Conditional restrictions (`restriction:conditional`) are recorded with a warning, not converted.
- If from/via/to members cannot resolve to produced links, add warning `UNRESOLVED_TURN_RESTRICTION` with the relation id.

Implement `TurnRestrictionIndex` with:

```java
public boolean isDisallowed(String mode, String currentLinkId, String nextLinkId)
```

- [ ] **Step 8: Implement network cleaner**

`OsmNetworkCleaner` rules:

- Remove links with missing endpoints, non-finite metrics, or non-positive length/freespeed. (Links cannot have empty mode sets after resolver changes — an access-excluded way is not converted at all, with a `MODE_EXCLUDED_WAY` warning.)
- For configured modes, keep the largest weakly connected component by link count.
- Quarantine smaller components into `OsmNetworkBuildResult.quarantinedComponents()` (removed from the primary network, retained in the result with their sidecar entries).
- With `keepWaysWithTransit=true` (default), transit-relevant quarantined components stay quarantined with a warning issue; only when the flag is false are they dropped, with issue `DROPPED_TRANSIT_RELEVANT_COMPONENT` carrying source OSM ids.
- Duplicate link identity is `(source way id, direction, segment index)`; parallel links from different ways are never duplicates.
- Call `network.postProcess()` after removals.

Add config flags:

```java
boolean cleanDisconnectedComponents
Set<String> cleaningModes
boolean keepWaysWithTransit
```

Defaults:

- `cleanDisconnectedComponents=true`
- `cleaningModes=Set.of("car")`
- `keepWaysWithTransit=true`

Also add `quarantinedComponents()` to `OsmNetworkBuildResult` and compact all sidecar tables (geometry, hints, id mappings) to surviving ids after cleaning.

- [ ] **Step 9: Implement uniform-grid query indexes**

Implement `NearestLink` and `NearestNode` records.

`LinkSpatialIndex`:

- Constructor consumes `Network`, `OsmGeometryStore`, and cell size meters.
- Index each polyline **segment** into the grid cells that segment traverses (Bresenham-style traversal), not the aggregate link bounding box; half-open cell math correct for negative coordinates; cap visited cells per query.
- `nearestLinks(Coord point, Set<String> modes, double radiusMeters, int maxResults)` returns results deduplicated by link id and sorted by `(distance, linkId)`.
- Distance is exact point-to-polyline distance recomputed from full sidecar geometry.

`NodeSpatialIndex`:

- Grid by node coordinate.
- Return sorted nearest nodes within radius; ties break by node id.

`StopHintIndex`:

- Grid by `OsmStopHint.coord()`.
- Optional mode filter by `servedModes`.

`LaneHintIndex`:

- Simple immutable maps by link id and node id.

`NetworkQueryIndex` facade methods:

```java
public List<NearestLink> nearestLinks(Coord point, Set<String> modes, double radiusMeters, int maxResults)
public List<NearestNode> nearestNodes(Coord point, double radiusMeters, int maxResults)
public List<OsmStopHint> nearestStopHints(Coord point, String mode, double radiusMeters, int maxResults)
public List<Link> outgoingLinks(Id<Node> nodeId, String mode)
public List<Link> incomingLinks(Id<Node> nodeId, String mode)
public OsmLaneHint laneHint(String linkId)
public OsmIntersectionLaneHint intersectionLaneHint(String nodeId)
public TurnRestrictionIndex turnRestrictions()
```

- [ ] **Step 10: Wire final Phase 1 result**

Modify `OsmMatsimNetworkBuilder` final order (normative pipeline from the spec):

1. Resolve rules, access/oneway/mode resolution, and retained routing nodes (restriction via-node set computed from relations BEFORE link creation; via-way chain nodes retained).
2. Build directed network and geometry store.
3. Extract lane/stop/intersection hints.
4. Build the normalized restriction model.
5. Clean network (quarantine, sidecar compaction).
6. Translate restrictions to `disallowedNextLinks` attributes against FINAL link ids; run restriction validation (every sequence contiguous, existing, mode-permitted).
7. Build indexes.
8. Return `OsmNetworkBuildResult` with cleaned network, quarantine list, sidecars, indexes, provenance, and issues.

- [ ] **Step 11: Run Phase 1F tests**

Run: `mvn -Dtest=OsmTurnRestrictionTest,OsmNetworkCleanerTest,NetworkQueryIndexTest test`

Expected: PASS.

- [ ] **Step 12: Run full Phase 1 test slice**

Run:

```bash
mvn -Dtest=OsmImportContractTest,OsmNetworkImporterTest,OsmMatsimNetworkBuilderTest,OsmHintExtractorTest,OsmGeometrySimplificationTest,OsmTurnRestrictionTest,OsmNetworkCleanerTest,NetworkQueryIndexTest,NoMatsimRuntimeImportsTest test
```

Expected: PASS.

- [ ] **Step 13: Run full verification**

Run: `mvn clean verify -B`

Expected: PASS.

- [ ] **Step 14: Commit**

Run:

```bash
git add src/main/java/com/citymodeler/matsim/models/network src/main/java/com/citymodeler/matsim/models/osm src/main/java/com/citymodeler/matsim/models/io src/test/java/com/citymodeler/matsim/models/network src/test/java/com/citymodeler/matsim/models/osm src/test/resources/osm
git commit -m "feat: clean and index OSM networks"
```

---

## Final Phase 1 Verification

- [ ] Run: `mvn clean verify -B`
- [ ] Run: `grep -R "org\.matsim" src/main/java` and confirm the only match is the single named constant `MATSIM_DISALLOWED_NEXT_LINKS_CLASS_HINT` in `XmlSupport` (the MATSim-compatible class hint required by the spec's wire contract).
- [ ] Inspect `pom.xml` and confirm no MATSim, Pt2MATSim, or MATSim contrib dependencies were added.
- [ ] Run the provenance round-trip test (`OsmImportContractTest` + Task 6 XML round trip) and confirm `NetworkXmlWriter` output includes OSM provenance attributes and the exact `disallowedNextLinks` class hint.
- [ ] Confirm Phase 1 output exposes `cleanedNetwork()`, `queryIndex()`, `geometryStore()`, `stopHints()`, `laneHintsByLinkId()`, `quarantinedComponents()`, and `turnRestrictionIndex()` for Phase 2.

## Known Execution Note

This workspace previously reported `mvn: command not found`. If Maven is still unavailable, execute tests in an environment with Maven installed before claiming completion.
