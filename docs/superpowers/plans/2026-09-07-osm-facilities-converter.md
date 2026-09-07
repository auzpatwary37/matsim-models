# OSM → MATSim Facilities Converter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable, spec-driven OSM→MATSim `facilities.xml` converter to the `matsim-models` library, so CityModeler can generate facility files (businesses + households) directly from OSM data.

**Architecture:** A new package `com.citymodeler.matsim.models.facilities.osm` with four focused classes: a streaming `OsmFacilityParser` (StAX) that reads `.osm` XML into lightweight `OsmFacility` records, an `OsmFacilityConfig` that holds tag→activity mapping + capacity rules + target CRS, an `OsmFacilityConverter` that maps parsed facilities to the existing `ActivityFacilities`/`ActivityFacility`/`ActivityOption` models, and an `OsmFacilityWriter` that reuses the existing `FacilitiesXmlWriter`. Coordinate reprojection from WGS84 to the target UTM CRS uses proj4j.

**Tech Stack:** Java 17, Maven, JUnit 5, proj4j 1.4.3 + proj4j-epsg 1.4.3, StAX (JDK built-in).

**Spec:** This plan implements the design agreed in the brainstorming session: businesses (amenity/shop/office/tourism/leisure + commercial/retail/industrial/office/school/university buildings) → work/leisure/education/health activity types with `opening_hours`; households (house/detached/semidetached_house/terrace/apartments/residential) → `home` activity with capacity = `floor(area_m2 × levels / 30)`; addresses stored as attributes; output in the target UTM CRS via proj4j.

## Global Constraints

- Java 17 (maven.compiler.release=17). No records with non-final fields; use `record` only for immutable data.
- Package root: `com.citymodeler.matsim.models.facilities.osm`.
- Reuse existing models: `ActivityFacilities`, `ActivityFacility`, `ActivityOption`, `Coord`, `Id`, `Attributes` — do NOT modify them.
- Reuse existing `FacilitiesXmlWriter` for output.
- proj4j version 1.4.3, proj4j-epsg version 1.4.3 (both Apache 2.0 / EPSG-distribution licensed).
- Checkstyle `failOnViolation=false` (warnings only) — but keep code clean (4-space indent, 160-char line limit).
- Tests: JUnit 5, in `src/test/java/com/citymodeler/matsim/models/facilities/osm/`.
- Build/test command: `mvn -q test` (from repo root `~/git/matsim-models`).
- OSM input is large (1.3 GB); the parser MUST stream (StAX), never load the whole file into memory.

---

### Task 1: Add proj4j dependencies to pom.xml

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Produces: `org.locationtech.proj4j.CRSFactory`, `org.locationtech.proj4j.CoordinateTransformFactory`, `org.locationtech.proj4j.CoordinateReferenceSystem`, `org.locationtech.proj4j.CoordinateTransform`, `org.locationtech.proj4j.ProjCoordinate` available on the classpath.

- [ ] **Step 1: Add proj4j version property**

In `pom.xml`, inside the existing `<properties>` block, add:

```xml
<proj4j.version>1.4.3</proj4j.version>
```

- [ ] **Step 2: Add proj4j dependencies**

In the `<dependencies>` block, add:

```xml
<dependency>
    <groupId>org.locationtech.proj4j</groupId>
    <artifactId>proj4j</artifactId>
    <version>${proj4j.version}</version>
</dependency>
<dependency>
    <groupId>org.locationtech.proj4j</groupId>
    <artifactId>proj4j-epsg</artifactId>
    <version>${proj4j.version}</version>
</dependency>
```

- [ ] **Step 3: Verify the build still compiles**

Run: `mvn -q -o compile` (offline) or `mvn -q compile`
Expected: BUILD SUCCESS (dependencies resolve from Maven Central).

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: add proj4j + proj4j-epsg 1.4.3 for CRS reprojection"
```

---

### Task 2: Create the OsmFacility record (parsed intermediate)

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacility.java`

**Interfaces:**
- Produces: `OsmFacility` record with accessors `id()`, `lon()`, `lat()`, `name()`, `type()`, `openingHours()`, `address()`, `areaM2()`, `levels()`, `isHousehold()`.
- `address()` returns a `Map<String,String>` of `addr:*` tags (e.g. `addr:street`, `addr:housenumber`, `addr:city`, `addr:postcode`).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityTest.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class OsmFacilityTest {
    @Test
    void recordExposesParsedFields() {
        OsmFacility f = new OsmFacility(
            "123", -79.4, 43.6, "Randolph Theatre", "theatre",
            "Mo-Fr 10:00-18:00",
            Map.of("addr:street", "Bathurst Street", "addr:housenumber", "736"),
            250.0, 2, false);
        assertEquals("123", f.id());
        assertEquals(-79.4, f.lon());
        assertEquals(43.6, f.lat());
        assertEquals("Randolph Theatre", f.name());
        assertEquals("theatre", f.type());
        assertEquals("Mo-Fr 10:00-18:00", f.openingHours());
        assertEquals("Bathurst Street", f.address().get("addr:street"));
        assertEquals(250.0, f.areaM2());
        assertEquals(2, f.levels());
        assertTrue(!f.isHousehold());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OsmFacilityTest`
Expected: FAIL — `OsmFacility` class not found.

- [ ] **Step 3: Create the record**

Create `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacility.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import java.util.Map;

/**
 * A single facility parsed from OSM, before conversion to a MATSim
 * {@link com.citymodeler.matsim.models.facilities.ActivityFacility}.
 *
 * @param id            OSM element id (node or way id).
 * @param lon           longitude in WGS84 decimal degrees.
 * @param lat           latitude in WGS84 decimal degrees.
 * @param name          OSM {@code name} tag, may be null.
 * @param type          OSM tag value that classifies the facility (e.g. {@code restaurant},
 *                      {@code house}, {@code school}).
 * @param openingHours  OSM {@code opening_hours} tag, may be null.
 * @param address       map of {@code addr:*} tags (street, housenumber, city, postcode).
 * @param areaM2        building footprint area in square metres (0 if unknown).
 * @param levels        number of building levels (0 if unknown).
 * @param isHousehold   true if this is a residential building (home facility).
 */
public record OsmFacility(
        String id,
        double lon,
        double lat,
        String name,
        String type,
        String openingHours,
        Map<String, String> address,
        double areaM2,
        int levels,
        boolean isHousehold) {
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OsmFacilityTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacility.java src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityTest.java
git commit -m "feat(facilities): add OsmFacility record for parsed OSM facilities"
```

---

### Task 3: Create the OsmFacilityConfig

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConfig.java`

**Interfaces:**
- Produces:
  - `OsmFacilityConfig(String targetCrs, double personsPerSqm, Map<String,String> tagToActivity, Set<String> householdBuildingTypes, Set<String> businessKeys)`
  - `String targetCrs()`
  - `double personsPerSqm()`
  - `String activityForType(String type)` — returns the MATSim activity type for an OSM tag value, or `"work"` if unmapped.
  - `boolean isHouseholdBuilding(String buildingValue)`
  - `boolean isBusinessKey(String key)`
  - `static OsmFacilityConfig defaults(String targetCrs)` — returns a config with the standard mappings.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConfigTest.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OsmFacilityConfigTest {
    @Test
    void defaultsMapBusinessTagsToActivities() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        assertEquals("work", cfg.activityForType("office"));
        assertEquals("leisure", cfg.activityForType("restaurant"));
        assertEquals("education", cfg.activityForType("school"));
        assertEquals("health", cfg.activityForType("hospital"));
        assertEquals("work", cfg.activityForType("unknown_thing"));
    }

    @Test
    void defaultsIdentifyHouseholdsAndBusinesses() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        assertTrue(cfg.isHouseholdBuilding("house"));
        assertTrue(cfg.isHouseholdBuilding("apartments"));
        assertFalse(cfg.isHouseholdBuilding("retail"));
        assertTrue(cfg.isBusinessKey("amenity"));
        assertTrue(cfg.isBusinessKey("shop"));
        assertFalse(cfg.isBusinessKey("building"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OsmFacilityConfigTest`
Expected: FAIL — `OsmFacilityConfig` not found.

- [ ] **Step 3: Create the config**

Create `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConfig.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for converting OSM facilities to MATSim facilities.
 *
 * @param targetCrs          target coordinate reference system (e.g. {@code EPSG:32617}).
 * @param personsPerSqm      persons per square metre used to estimate household capacity.
 * @param tagToActivity      maps OSM tag values to MATSim activity types.
 * @param householdBuildingTypes OSM {@code building} values treated as households.
 * @param businessKeys       OSM tag keys that mark a facility as a business/POI.
 */
public record OsmFacilityConfig(
        String targetCrs,
        double personsPerSqm,
        Map<String, String> tagToActivity,
        Set<String> householdBuildingTypes,
        Set<String> businessKeys) {

    public String activityForType(String type) {
        return tagToActivity.getOrDefault(type, "work");
    }

    public boolean isHouseholdBuilding(String buildingValue) {
        return householdBuildingTypes.contains(buildingValue);
    }

    public boolean isBusinessKey(String key) {
        return businessKeys.contains(key);
    }

    public static OsmFacilityConfig defaults(String targetCrs) {
        return new OsmFacilityConfig(
            targetCrs,
            1.0 / 30.0,
            Map.ofEntries(
                Map.entry("office", "work"),
                Map.entry("commercial", "work"),
                Map.entry("retail", "work"),
                Map.entry("industrial", "work"),
                Map.entry("restaurant", "leisure"),
                Map.entry("cafe", "leisure"),
                Map.entry("bar", "leisure"),
                Map.entry("fast_food", "leisure"),
                Map.entry("tourism", "leisure"),
                Map.entry("leisure", "leisure"),
                Map.entry("school", "education"),
                Map.entry("university", "education"),
                Map.entry("college", "education"),
                Map.entry("kindergarten", "education"),
                Map.entry("hospital", "health"),
                Map.entry("clinic", "health"),
                Map.entry("pharmacy", "health"),
                Map.entry("bank", "work"),
                Map.entry("shop", "work")),
            Set.of("house", "detached", "semidetached_house", "terrace", "apartments", "residential"),
            Set.of("amenity", "shop", "office", "tourism", "leisure"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OsmFacilityConfigTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConfig.java src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConfigTest.java
git commit -m "feat(facilities): add OsmFacilityConfig with tag->activity and household rules"
```

---

### Task 4: Create the OsmFacilityParser (streaming StAX)

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityParser.java`

**Interfaces:**
- Consumes: `OsmFacilityConfig` (from Task 3), `OsmFacility` (from Task 2).
- Produces: `List<OsmFacility> parse(Path osmFile)` — streams the `.osm` XML and returns all business + household facilities.
- Also produces a package-private helper `static double polygonAreaM2(List<double[]> ring)` used to compute way footprint area from node coordinates (lon/lat → approximate metres).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityParserTest.java` and a fixture `src/test/resources/fixtures/osm-facilities.osm`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="test">
  <node id="1" lat="43.6" lon="-79.4">
    <tag k="amenity" v="restaurant"/>
    <tag k="name" v="Test Cafe"/>
    <tag k="opening_hours" v="Mo-Fr 09:00-17:00"/>
    <tag k="addr:street" v="Bathurst Street"/>
    <tag k="addr:housenumber" v="100"/>
  </node>
  <node id="2" lat="43.7" lon="-79.5">
    <tag k="shop" v="bakery"/>
  </node>
  <!-- OSM files always emit all nodes before any ways; the single-pass
       parser relies on that invariant to resolve a way's node refs. -->
  <node id="100" lat="43.60" lon="-79.40"/>
  <node id="101" lat="43.60" lon="-79.39"/>
  <node id="102" lat="43.61" lon="-79.40"/>
  <way id="10">
    <nd ref="100"/>
    <nd ref="101"/>
    <nd ref="102"/>
    <nd ref="100"/>
    <tag k="building" v="house"/>
    <tag k="building:levels" v="2"/>
  </way>
</osm>
```

Test:

```java
package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class OsmFacilityParserTest {
    @Test
    void parsesBusinessNodesAndHouseholdWays() throws Exception {
        Path fixture = Path.of(getClass().getClassLoader()
            .getResource("fixtures/osm-facilities.osm").toURI());
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        List<OsmFacility> facilities = new OsmFacilityParser(cfg).parse(fixture);

        // 2 business nodes + 1 household way
        assertEquals(3, facilities.size());

        OsmFacility cafe = facilities.stream()
            .filter(f -> f.type().equals("restaurant")).findFirst().orElseThrow();
        assertEquals("Test Cafe", cafe.name());
        assertEquals("Mo-Fr 09:00-17:00", cafe.openingHours());
        assertEquals("Bathurst Street", cafe.address().get("addr:street"));
        assertTrue(!cafe.isHousehold());

        OsmFacility house = facilities.stream()
            .filter(OsmFacility::isHousehold).findFirst().orElseThrow();
        assertEquals(2, house.levels());
        assertTrue(house.areaM2() > 0, "house footprint area should be computed");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OsmFacilityParserTest`
Expected: FAIL — `OsmFacilityParser` not found.

- [ ] **Step 3: Create the parser**

Create `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityParser.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Streaming parser that reads an OSM {@code .osm} XML file and produces
 * {@link OsmFacility} records for businesses (nodes/ways with business tags)
 * and households (residential building ways).
 *
 * <p>Uses StAX so it can handle multi-gigabyte OSM extracts without loading
 * the whole file into memory.</p>
 */
public final class OsmFacilityParser {

    private static final double EARTH_RADIUS_M = 6371000.0;

    private final OsmFacilityConfig config;

    public OsmFacilityParser(OsmFacilityConfig config) {
        this.config = config;
    }

    public List<OsmFacility> parse(Path osmFile) {
        List<OsmFacility> result = new ArrayList<>();
        Map<String, double[]> nodeCoords = new HashMap<>();
        try (InputStream in = java.nio.file.Files.newInputStream(osmFile)) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("node".equals(name)) {
                        handleNode(reader, nodeCoords, result);
                    } else if ("way".equals(name)) {
                        handleWay(reader, nodeCoords, result);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OSM file: " + osmFile, e);
        }
        return result;
    }

    private void handleNode(XMLStreamReader reader, Map<String, double[]> nodeCoords,
                            List<OsmFacility> result) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        double lat = Double.parseDouble(reader.getAttributeValue(null, "lat"));
        double lon = Double.parseDouble(reader.getAttributeValue(null, "lon"));
        Map<String, String> tags = readTags(reader);
        nodeCoords.put(id, new double[]{lon, lat});

        OsmFacility facility = toFacility(id, lon, lat, tags, 0.0, 0, false);
        if (facility != null) {
            result.add(facility);
        }
    }

    private void handleWay(XMLStreamReader reader, Map<String, double[]> nodeCoords,
                           List<OsmFacility> result) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        List<String> nodeRefs = new ArrayList<>();
        Map<String, String> tags = new LinkedHashMap<>();
        boolean done = false;
        while (reader.hasNext() && !done) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                if ("nd".equals(name)) {
                    nodeRefs.add(reader.getAttributeValue(null, "ref"));
                } else if ("tag".equals(name)) {
                    tags.put(reader.getAttributeValue(null, "k"),
                             reader.getAttributeValue(null, "v"));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "way".equals(reader.getLocalName())) {
                done = true;
            }
        }

        // centroid of the way's nodes
        double sumLon = 0, sumLat = 0;
        int n = 0;
        for (String ref : nodeRefs) {
            double[] c = nodeCoords.get(ref);
            if (c != null) {
                sumLon += c[0];
                sumLat += c[1];
                n++;
            }
        }
        if (n == 0) {
            return;
        }
        double lon = sumLon / n;
        double lat = sumLat / n;

        double area = 0.0;
        int levels = 0;
        if (config.isHouseholdBuilding(tags.get("building"))) {
            area = polygonAreaM2(nodeRefs, nodeCoords);
            levels = parseInt(tags.get("building:levels"));
        }

        OsmFacility facility = toFacility(id, lon, lat, tags, area, levels, true);
        if (facility != null) {
            result.add(facility);
        }
    }

    private OsmFacility toFacility(String id, double lon, double lat, Map<String, String> tags,
                                   double area, int levels, boolean isWay) {
        // Determine if business or household
        boolean isBusiness = false;
        String type = null;
        for (String key : config.businessKeys()) {
            if (tags.containsKey(key)) {
                isBusiness = true;
                type = tags.get(key);
                break;
            }
        }
        boolean isHousehold = !isBusiness && config.isHouseholdBuilding(tags.get("building"));
        if (!isBusiness && !isHousehold) {
            return null;
        }

        Map<String, String> address = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : tags.entrySet()) {
            if (e.getKey().startsWith("addr:")) {
                address.put(e.getKey(), e.getValue());
            }
        }

        return new OsmFacility(
            id, lon, lat,
            tags.get("name"),
            type,
            tags.get("opening_hours"),
            address,
            area,
            levels,
            isHousehold);
    }

    private Map<String, String> readTags(XMLStreamReader reader) throws XMLStreamException {
        Map<String, String> tags = new LinkedHashMap<>();
        boolean done = false;
        while (reader.hasNext() && !done) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "tag".equals(reader.getLocalName())) {
                tags.put(reader.getAttributeValue(null, "k"),
                         reader.getAttributeValue(null, "v"));
            } else if (event == XMLStreamConstants.END_ELEMENT && "node".equals(reader.getLocalName())) {
                done = true;
            }
        }
        return tags;
    }

    private static int parseInt(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Approximate area (m²) of a closed ring of lon/lat points using the
     * spherical excess formula. Returns 0 if the ring is degenerate.
     */
    static double polygonAreaM2(List<String> nodeRefs, Map<String, double[]> nodeCoords) {
        List<double[]> ring = new ArrayList<>();
        for (String ref : nodeRefs) {
            double[] c = nodeCoords.get(ref);
            if (c != null) {
                ring.add(c);
            }
        }
        if (ring.size() < 3) {
            return 0.0;
        }
        double area = 0.0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            double[] a = ring.get(i);
            double[] b = ring.get((i + 1) % n);
            double ax = Math.toRadians(a[0]);
            double ay = Math.toRadians(a[1]);
            double bx = Math.toRadians(b[0]);
            double by = Math.toRadians(b[1]);
            area += Math.toRadians(b[0] - a[0]) * (2 + Math.sin(ay) + Math.sin(by));
        }
        area = Math.abs(area * EARTH_RADIUS_M * EARTH_RADIUS_M / 2.0);
        return area;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OsmFacilityParserTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityParser.java src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityParserTest.java src/test/resources/fixtures/osm-facilities.osm
git commit -m "feat(facilities): add streaming OsmFacilityParser for OSM nodes and ways"
```

---

### Task 5: Create the OsmFacilityConverter (maps to ActivityFacilities)

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConverter.java`

**Interfaces:**
- Consumes: `OsmFacility` (Task 2), `OsmFacilityConfig` (Task 3).
- Produces: `ActivityFacilities convert(List<OsmFacility> facilities)` — returns facilities in the target CRS (reprojected from WGS84 via proj4j), with activity options and address attributes.
- Also produces `static double estimateHouseholdCapacity(double areaM2, int levels, double personsPerSqm)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConverterTest.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;

class OsmFacilityConverterTest {
    @Test
    void convertsBusinessToWorkFacilityWithAddress() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        OsmFacilityConverter converter = new OsmFacilityConverter(cfg);
        OsmFacility cafe = new OsmFacility(
            "1", -79.4, 43.6, "Test Cafe", "restaurant",
            "Mo-Fr 09:00-17:00",
            Map.of("addr:street", "Bathurst Street", "addr:housenumber", "100"),
            0.0, 0, false);

        ActivityFacilities facilities = converter.convert(List.of(cafe));
        ActivityFacility f = facilities.getFacilities().values().iterator().next();

        assertEquals("leisure", f.getActivityOptions().get("leisure").getType());
        assertEquals("Mo-Fr 09:00-17:00", f.getAttributes().getAttribute("opening_hours"));
        assertEquals("Bathurst Street", f.getAttributes().getAttribute("addr:street"));
        // reprojected to UTM 17N: x should be positive, y large
        assertTrue(f.getCoord().getX() > 0);
        assertTrue(f.getCoord().getY() > 1000000);
    }

    @Test
    void convertsHouseholdToHomeFacilityWithCapacity() {
        OsmFacilityConfig cfg = OsmFacilityConfig.defaults("EPSG:32617");
        OsmFacilityConverter converter = new OsmFacilityConverter(cfg);
        OsmFacility house = new OsmFacility(
            "10", -79.4, 43.6, null, "house", null,
            Map.of(), 300.0, 2, true);

        ActivityFacilities facilities = converter.convert(List.of(house));
        ActivityFacility f = facilities.getFacilities().values().iterator().next();

        assertEquals("home", f.getActivityOptions().get("home").getType());
        double expected = OsmFacilityConverter.estimateHouseholdCapacity(300.0, 2, cfg.personsPerSqm());
        assertEquals(expected, f.getActivityOptions().get("home").getCapacity(), 0.001);
        assertTrue(expected > 0);
    }

    @Test
    void estimateHouseholdCapacityUsesAreaTimesLevels() {
        double cap = OsmFacilityConverter.estimateHouseholdCapacity(300.0, 2, 1.0 / 30.0);
        assertEquals(20.0, cap, 0.001); // 300 * 2 / 30
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OsmFacilityConverterTest`
Expected: FAIL — `OsmFacilityConverter` not found.

- [ ] **Step 3: Create the converter**

Create `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConverter.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import java.util.List;
import java.util.Map;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.facilities.ActivityOption;

/**
 * Converts parsed {@link OsmFacility} records into MATSim
 * {@link ActivityFacilities}, reprojecting coordinates from WGS84 to the
 * configured target CRS using proj4j.
 */
public final class OsmFacilityConverter {

    private final OsmFacilityConfig config;
    private final CoordinateTransform transform;

    public OsmFacilityConverter(OsmFacilityConfig config) {
        this.config = config;
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem wgs84 = crsFactory.createFromName("EPSG:4326");
        CoordinateReferenceSystem target = crsFactory.createFromName(config.targetCrs());
        this.transform = new CoordinateTransformFactory().createTransform(wgs84, target);
    }

    public ActivityFacilities convert(List<OsmFacility> facilities) {
        ActivityFacilities result = new ActivityFacilities("osm-facilities");
        for (OsmFacility osm : facilities) {
            ActivityFacility facility = toActivityFacility(osm);
            if (facility != null) {
                result.addFacility(facility);
            }
        }
        return result;
    }

    private ActivityFacility toActivityFacility(OsmFacility osm) {
        ProjCoordinate src = new ProjCoordinate(osm.lon(), osm.lat());
        ProjCoordinate dst = new ProjCoordinate();
        transform.transform(src, dst);

        ActivityFacility facility = new ActivityFacility(
            Id.create(osm.id(), ActivityFacility.class),
            new Coord(dst.x, dst.y));

        if (osm.isHousehold()) {
            ActivityOption home = new ActivityOption("home");
            home.setCapacity(estimateHouseholdCapacity(
                osm.areaM2(), osm.levels(), config.personsPerSqm()));
            facility.addActivityOption(home);
        } else {
            String activityType = config.activityForType(osm.type());
            facility.addActivityOption(new ActivityOption(activityType));
        }

        if (osm.name() != null) {
            facility.setDesc(osm.name());
        }
        if (osm.openingHours() != null) {
            facility.getAttributes().putAttribute("opening_hours", osm.openingHours());
        }
        for (Map.Entry<String, String> e : osm.address().entrySet()) {
            facility.getAttributes().putAttribute(e.getKey(), e.getValue());
        }
        return facility;
    }

    /**
     * Estimates the maximum number of persons a household can hold from its
     * footprint area and number of levels.
     */
    public static double estimateHouseholdCapacity(double areaM2, int levels, double personsPerSqm) {
        double effectiveArea = areaM2 * Math.max(1, levels);
        return Math.floor(effectiveArea * personsPerSqm);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OsmFacilityConverterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConverter.java src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityConverterTest.java
git commit -m "feat(facilities): add OsmFacilityConverter with proj4j reprojection and capacity"
```

---

### Task 6: Create the OsmFacilityWriter (reuses FacilitiesXmlWriter)

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityWriter.java`

**Interfaces:**
- Consumes: `ActivityFacilities` (existing model).
- Produces: `void write(ActivityFacilities facilities, Path path)` — writes `facilities.xml` via the existing `FacilitiesXmlWriter`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityWriterTest.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.facilities.ActivityOption;
import com.citymodeler.matsim.models.io.FacilitiesXmlReader;

class OsmFacilityWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesReadableFacilitiesXml() throws Exception {
        ActivityFacilities facilities = new ActivityFacilities("test");
        ActivityFacility f = new ActivityFacility(
            Id.create("1", ActivityFacility.class), new Coord(600000.0, 4800000.0));
        f.addActivityOption(new ActivityOption("home"));
        facilities.addFacility(f);

        Path out = tempDir.resolve("facilities.xml");
        new OsmFacilityWriter().write(facilities, out);

        assertTrue(Files.exists(out));
        ActivityFacilities read = new FacilitiesXmlReader().read(Files.newInputStream(out));
        assertEquals(1, read.getFacilities().size());
        assertEquals("home", read.getFacilities().values().iterator().next()
            .getActivityOptions().get("home").getType());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OsmFacilityWriterTest`
Expected: FAIL — `OsmFacilityWriter` not found.

- [ ] **Step 3: Create the writer**

Create `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityWriter.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import java.nio.file.Path;

import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.io.FacilitiesXmlWriter;

/**
 * Writes {@link ActivityFacilities} to a MATSim {@code facilities.xml} file,
 * delegating to the existing {@link FacilitiesXmlWriter}.
 */
public final class OsmFacilityWriter {

    private final FacilitiesXmlWriter delegate = new FacilitiesXmlWriter();

    public void write(ActivityFacilities facilities, Path path) {
        delegate.write(facilities, path);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OsmFacilityWriterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityWriter.java src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilityWriterTest.java
git commit -m "feat(facilities): add OsmFacilityWriter delegating to FacilitiesXmlWriter"
```

---

### Task 7: Add a CLI runner for generating facility files

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilitiesMain.java`

**Interfaces:**
- Consumes: `OsmFacilityParser`, `OsmFacilityConverter`, `OsmFacilityWriter`, `OsmFacilityConfig`.
- Produces: a `main(String[] args)` that reads `args[0]` = OSM file, `args[1]` = output facilities.xml, `args[2]` = target CRS (e.g. `EPSG:32617`), and writes the facilities file.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilitiesMainTest.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OsmFacilitiesMainTest {
    @TempDir
    Path tempDir;

    @Test
    void mainGeneratesFacilitiesFile() throws Exception {
        Path osm = Path.of(getClass().getClassLoader()
            .getResource("fixtures/osm-facilities.osm").toURI());
        Path out = tempDir.resolve("facilities.xml");
        OsmFacilitiesMain.main(new String[]{osm.toString(), out.toString(), "EPSG:32617"});
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=OsmFacilitiesMainTest`
Expected: FAIL — `OsmFacilitiesMain` not found.

- [ ] **Step 3: Create the main class**

Create `src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilitiesMain.java`:

```java
package com.citymodeler.matsim.models.facilities.osm;

import java.nio.file.Path;
import java.util.List;

import com.citymodeler.matsim.models.facilities.ActivityFacilities;

/**
 * Command-line entry point to generate a MATSim facilities file from an OSM
 * extract.
 *
 * <p>Usage: {@code OsmFacilitiesMain <osm-file> <output-facilities.xml> <target-crs>}</p>
 */
public final class OsmFacilitiesMain {

    private OsmFacilitiesMain() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "Usage: OsmFacilitiesMain <osm-file> <output-facilities.xml> <target-crs>");
        }
        Path osmFile = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String targetCrs = args[2];

        OsmFacilityConfig config = OsmFacilityConfig.defaults(targetCrs);
        List<OsmFacility> parsed = new OsmFacilityParser(config).parse(osmFile);
        ActivityFacilities facilities = new OsmFacilityConverter(config).convert(parsed);
        new OsmFacilityWriter().write(facilities, output);

        System.out.println("Wrote " + facilities.getFacilities().size()
            + " facilities to " + output);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=OsmFacilitiesMainTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilitiesMain.java src/test/java/com/citymodeler/matsim/models/facilities/osm/OsmFacilitiesMainTest.java
git commit -m "feat(facilities): add OsmFacilitiesMain CLI runner"
```

---

### Task 8: Run the full test suite and verify

**Files:**
- None (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `mvn -q test`
Expected: All tests pass, including the new `OsmFacility*` tests and all pre-existing tests.

- [ ] **Step 2: Verify no regressions in existing facilities I/O**

Run: `mvn -q test -Dtest=FacilitiesXmlTest`
Expected: PASS.

- [ ] **Step 3: Commit any remaining changes**

```bash
git status
git add -A
git commit -m "test(facilities): verify OSM facility converter suite"
```

---

## Self-Review

**Spec coverage:**
- Businesses (amenity/shop/office/tourism/leisure + commercial/retail/industrial/office/school/university) → Task 3 (config businessKeys + tagToActivity) + Task 4 (parser) + Task 5 (converter). ✓
- Households (house/detached/semidetached_house/terrace/apartments/residential) → Task 3 (householdBuildingTypes) + Task 4 (way parsing) + Task 5 (home activity). ✓
- opening_hours → Task 4 (parser reads tag) + Task 5 (stored as attribute). ✓
- Household capacity = area × levels / 30 → Task 5 `estimateHouseholdCapacity`. ✓
- Address attributes → Task 4 (parser collects addr:* tags) + Task 5 (stored as attributes). ✓
- proj4j reprojection to target CRS → Task 1 (deps) + Task 5 (converter). ✓
- Reuse FacilitiesXmlWriter → Task 6. ✓
- CLI runner for CityModeler → Task 7. ✓

**Placeholder scan:** No TBD/TODO; all code blocks are complete. ✓

**Type consistency:** `OsmFacility` record fields match across Tasks 2, 4, 5. `OsmFacilityConfig` methods (`activityForType`, `isHouseholdBuilding`, `isBusinessKey`, `personsPerSqm`, `targetCrs`) used consistently. `estimateHouseholdCapacity(areaM2, levels, personsPerSqm)` signature consistent in Task 5 test and impl. ✓
