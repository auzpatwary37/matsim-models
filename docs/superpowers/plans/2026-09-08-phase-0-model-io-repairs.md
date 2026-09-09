# Phase 0 Model And IO Repairs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the existing model/IO layer so Phases 1-2 can represent their outputs: transit network routes, vehicle types, Scenario vehicles, duplicate guards, mode preservation, child-stop parents, the `DisallowedNextLinks` attribute codec, and streaming writers.

**Architecture:** All changes are on existing classes in `com.citymodeler.matsim.models.*`, backward compatible where the MATSim format allows, wire-format-correcting where the current writer diverges from MATSim's documented format. No MATSim dependencies; the one `org.matsim.*` string literal (class hint) is a single named allowlist constant.

**Tech Stack:** Java 17, JUnit 5, existing DOM XML support (`XmlSupport`, `NetworkXmlWriter/Reader`, `TransitScheduleXmlWriter/Reader`, `VehiclesXmlWriter`), Jackson XML mapper (secondary path).

**Spec:** `docs/superpowers/specs/2026-09-08-gtfs-transit-mapping-clean-room-design.md` (section "Phase 0: Model And IO Repairs (Prerequisite)")

## Global Constraints

- No `org.matsim.*` imports in main source; exactly one allowlisted `org.matsim.*` string constant permitted (`MATSIM_DISALLOWED_NEXT_LINKS_CLASS_HINT` in `XmlSupport`).
- No Pt2MATSim, MATSim, or MATSim contrib dependencies in `pom.xml`.
- Do not inspect or copy MATSim GPL or Pt2MATSim source; use MATSim public documentation for wire format.
- All tests must pass in isolation; each task creates its own fixtures.
- Deterministic output: no `Instant.now()` in tested paths; map iteration order stable.
- `mvn clean verify -B` must pass after every task.

---

## File Structure

Modify:

- `src/main/java/com/citymodeler/matsim/models/transit/TransitRoute.java`: add link-sequence storage.
- `src/main/java/com/citymodeler/matsim/models/io/TransitScheduleXmlWriter.java`: `<route>` emission, `linkRefId`, child `transportMode`.
- `src/main/java/com/citymodeler/matsim/models/io/TransitScheduleXmlReader.java`: `<route>` parsing (tolerant of the old attribute forms).
- `src/main/java/com/citymodeler/matsim/models/vehicles/VehicleType.java` (new): type model with capacities.
- `src/main/java/com/citymodeler/matsim/models/vehicles/VehicleDefinitions.java`: type collection.
- `src/main/java/com/citymodeler/matsim/models/io/VehiclesXmlWriter.java`: `vehicleType` emission.
- `src/main/java/com/citymodeler/matsim/models/io/VehiclesXmlReader.java`: `vehicleType` parsing.
- `src/main/java/com/citymodeler/matsim/models/scenario/Scenario.java`: vehicles field.
- `src/main/java/com/citymodeler/matsim/models/scenario/ScenarioUtils.java`: bundle save.
- `src/main/java/com/citymodeler/matsim/models/io/ScenarioXmlReader.java`: vehicles module loading.
- `src/main/java/com/citymodeler/matsim/models/transit/TransitSchedule.java`, `TransitLine.java`: duplicate-key guards.
- `src/main/java/com/citymodeler/matsim/models/network/Link.java`: preserve empty modes.
- `src/main/java/com/citymodeler/matsim/models/io/NetworkXmlWriter.java`: always emit `modes`.
- `src/main/java/com/citymodeler/matsim/models/transit/TransitStopFacility.java`: `parentId`.
- `src/main/java/com/citymodeler/matsim/models/io/XmlSupport.java`: `DisallowedNextLinks` codec (write + read) and allowlist constant.
- `src/main/java/com/citymodeler/matsim/models/io/AttributesSerializer.java`, `AttributesDeserializer.java`: secondary codec consistency.
- `src/main/java/com/citymodeler/matsim/models/network/turnrestrictions/DisallowedNextLinks.java` (new): value object (JSON wire format).
- `src/main/java/com/citymodeler/matsim/models/validation/NetworkValidator.java`: artificial-link options.
- `src/main/java/com/citymodeler/matsim/models/api/MatsimIds.java` (new): deterministic typed-tuple id codec.
- `src/main/java/com/citymodeler/matsim/models/io/StreamingNetworkWriter.java` (new), `StreamingTransitScheduleWriter.java` (new): StAX streaming output.

Test files mirror each under `src/test/java/...`.

---

### Task 1: `DisallowedNextLinks` Value Object And XmlSupport Codec

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/network/turnrestrictions/DisallowedNextLinks.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/XmlSupport.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/AttributesSerializer.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/AttributesDeserializer.java`
- Test: `src/test/java/com/citymodeler/matsim/models/network/turnrestrictions/DisallowedNextLinksTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/io/DisallowedNextLinksXmlRoundTripTest.java`

**Interfaces:**
- Produces: `DisallowedNextLinks.empty()`, `plus(String mode, List<String> seq)`, `isDisallowed(String mode, List<String> seq)`, `asMap()`, `fromJson(String)`, `toJson()`.
- Produces: `XmlSupport.MATSIM_DISALLOWED_NEXT_LINKS_CLASS_HINT` = `"org.matsim.core.network.turnRestrictions.DisallowedNextLinks"`; reader alias `"org.matsim.core.network.DisallowedNextLinks"`.
- Produces: attribute name constant `ATTR_DISALLOWED_NEXT_LINKS = "disallowedNextLinks"`.
- Phase 1F consumes the value object; the network writer path is fixed here.

- [ ] **Step 1: Write failing tests**

Value-object tests: empty; plus single mode single sequence; plus multiple modes; sequence-of-one; `isDisallowed` true/false cases; `fromJson` round trip; malformed JSON throws typed exception; deterministic `toJson` key order (sorted modes, sequences in insertion order).

Round-trip test: build a `Network` with one link whose attributes contain a `DisallowedNextLinks` value; `NetworkXmlWriter.writeToString` must contain `class="org.matsim.core.network.turnRestrictions.DisallowedNextLinks"` and the JSON payload; read back through `NetworkXmlReader` and assert the attribute is a `DisallowedNextLinks` instance with equal contents. Also read a fixture carrying the historic alias hint and assert it parses.

- [ ] **Step 2: Run failing tests**

Run: `mvn -Dtest=DisallowedNextLinksTest,DisallowedNextLinksXmlRoundTripTest test`
Expected: compile failure.

- [ ] **Step 3: Implement**

- `DisallowedNextLinks`: immutable, backed by sorted map mode → list of sequences; `toJson` uses Jackson `ObjectMapper` with sorted keys; `fromJson` validates shape (`Map<String, List<List<String>>>`).
- `XmlSupport.classHint`: add `DisallowedNextLinks` branch returning the constant.
- `XmlSupport.appendAttributes`: for `DisallowedNextLinks` values write `toJson()`.
- `XmlSupport.convert`: for attribute name `disallowedNextLinks` or accepted hints, return `DisallowedNextLinks.fromJson(text)`; malformed → warning log + String passthrough (documented).
- Jackson serializer/deserializer: same mapping (secondary path).

- [ ] **Step 4: Run tests**

Run: `mvn -Dtest=DisallowedNextLinksTest,DisallowedNextLinksXmlRoundTripTest,AttributesSerdeTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/network/turnrestrictions src/main/java/com/citymodeler/matsim/models/io src/test/java/com/citymodeler/matsim/models/network/turnrestrictions src/test/java/com/citymodeler/matsim/models/io
git commit -m "feat: DisallowedNextLinks value object and XML codec"
```

---

### Task 2: Transit Network Route Model And MATSim Wire Format

**Files:**
- Modify: `src/main/java/com/citymodeler/matsim/models/transit/TransitRoute.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/TransitScheduleXmlWriter.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/TransitScheduleXmlReader.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/TransitSchedule.java` (only if a duplicate guard is shared; guards are Task 4)
- Test: `src/test/java/com/citymodeler/matsim/models/transit/TransitRouteNetworkSequenceTest.java`
- Modify: `src/test/java/com/citymodeler/matsim/models/io/TransitScheduleXmlTest.java` (add cases; do not delete existing cases this task)

**Interfaces:**
- Produces: `TransitRoute.setNetworkRoute(List<Id<Link>>)` / `getNetworkRoute()` (immutable view, ordered, nullable until mapped).
- Produces: writer emits, after `routeProfile` and before `departures`:
  `<route><link refId="..."/></route>` when the sequence is set.
- Produces: reader parses `<route>` into `setNetworkRoute`.
- Phase 2D consumes this for mapped routes.

- [ ] **Step 1: Write failing tests**

Model test: set sequence, read it back immutable (attempt to mutate returned list throws); null until set; setter rejects empty list (use null to clear).

Writer format test — assert the exact MATSim-documented form:

- `transportMode` as a child element `<transportMode>bus</transportMode>`, not an attribute
- stop facilities with `linkRefId="..."` attribute (not `linkId`)
- `<route><link refId="l1"/><link refId="l2"/></route>` between `routeProfile` and `departures`

Reader test: parse a fixture using `linkRefId` + child `transportMode` + `<route>` (modeled on the existing MATSim-generated fixture in `TransitScheduleXmlTest`) and assert all three land in the model. Keep the old attribute forms readable (tolerant parse) but do not write them.

- [ ] **Step 2: Run failing tests**

Run: `mvn -Dtest=TransitRouteNetworkSequenceTest,TransitScheduleXmlTest test`
Expected: new tests fail; note which existing tests assert the OLD attribute form — update those assertions in this task (they currently lock in divergence).

- [ ] **Step 3: Implement**

- `TransitRoute`: private `List<Id<Link>> networkRoute` (nullable); getter returns unmodifiable copy; setter stores defensive copy, rejects empty.
- Writer: emit child `transportMode` element; `linkRefId` attribute for stop facilities (rename from `linkId`); `<route>` block with `<link refId="..."/>` children in order.
- Reader: accept both `linkRefId` and legacy `linkId`; both child-element and legacy attribute `transportMode`; parse `<route><link refId/></route>`.
- Update existing test assertions that required the divergent writer forms.

- [ ] **Step 4: Run tests**

Run: `mvn -Dtest=TransitRouteNetworkSequenceTest,TransitScheduleXmlTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/transit src/main/java/com/citymodeler/matsim/models/io/TransitScheduleXmlWriter.java src/main/java/com/citymodeler/matsim/models/io/TransitScheduleXmlReader.java src/test/java/com/citymodeler/matsim/models/transit src/test/java/com/citymodeler/matsim/models/io/TransitScheduleXmlTest.java
git commit -m "feat: transit route network sequences and MATSim wire format"
```

---

### Task 3: VehicleType Model, Scenario Vehicles, Referential Validation

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/vehicles/VehicleType.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/vehicles/VehicleDefinitions.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/VehiclesXmlWriter.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/VehiclesXmlReader.java` (if present; create if missing)
- Modify: `src/main/java/com/citymodeler/matsim/models/scenario/Scenario.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/scenario/ScenarioUtils.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/ScenarioXmlReader.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/validation/ScenarioValidator.java` (add cross-check)
- Test: `src/test/java/com/citymodeler/matsim/models/vehicles/VehicleTypeTest.java`
- Modify: `src/test/java/com/citymodeler/matsim/models/vehicles/VehiclesXmlTest.java`
- Modify: `src/test/java/com/citymodeler/matsim/models/scenario/ScenarioModelTest.java`

**Interfaces:**
- Produces: `VehicleType(Id<VehicleType> id)` with `seatingCapacity`, `standingCapacity`, `lengthMeters`, `widthMeters`, `accessTimeSeconds`, `egressTimeSeconds` (doubles with setters, positive validation where MATSim requires).
- Produces: `VehicleDefinitions.addVehicleType(VehicleType)` / `getVehicleTypes()` (duplicate-key exception).
- Produces: `Scenario.setVehicleDefinitions(...)` / `getVehicleDefinitions()`; `ScenarioUtils.saveScenario(...)` writes network + transit schedule + vehicles files.
- Phase 2B consumes types/capacities; Phase 3 consumes the bundle save.

- [ ] **Step 1: Write failing tests**

- `VehicleType` validation: non-negative capacities; positive dimensions where set.
- `VehicleDefinitions` duplicate type id throws.
- Vehicles XML round trip: file with `vehicleType` (id, seating/standing capacity via `capacity` child element with `seats`/`standingRoom` persons) and `vehicle` elements referencing types parses and re-writes byte-stably (modulo formatting).
- Scenario: set/get vehicles; `ScenarioXmlReader` loads a `vehicles` module when its config param is present.
- `ScenarioValidator`: missing vehicle reference (departure → vehicle, vehicle → type) produces a validation issue.

- [ ] **Step 2: Run failing tests**

Run: `mvn -Dtest=VehicleTypeTest,VehiclesXmlTest,ScenarioModelTest test`
Expected: compile failure for new API; existing tests pass.

- [ ] **Step 3: Implement**

- `VehicleType` model with MATSim vehicle-definition field names (`capacity` → `seats`, `standingRoom`; `length`/`width` meters; `accessTime`/`egressTime` seconds).
- `VehicleDefinitions` gains a sorted type map with duplicate guards.
- Writer emits `<vehicleType id="...">` with `<capacity seats="..." standingRoom="..."/>` and dimension elements before vehicles; reader parses them.
- `Scenario` vehicles field + reader module + validator cross-checks.

- [ ] **Step 4: Run tests**

Run: `mvn -Dtest=VehicleTypeTest,VehiclesXmlTest,ScenarioModelTest,MatsimModelExceptionTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/vehicles src/main/java/com/citymodeler/matsim/models/scenario src/main/java/com/citymodeler/matsim/models/io src/main/java/com/citymodeler/matsim/models/validation src/test/java/com/citymodeler/matsim/models/vehicles src/test/java/com/citymodeler/matsim/models/scenario
git commit -m "feat: vehicle types, scenario vehicles, referential validation"
```

---

### Task 4: Duplicate-Key Guards, Empty-Modes Preservation, Child-Stop Parent

**Files:**
- Modify: `src/main/java/com/citymodeler/matsim/models/transit/TransitSchedule.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/transit/TransitLine.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/transit/TransitRoute.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/network/Link.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/NetworkXmlWriter.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/NetworkXmlReader.java` (preserve empty modes on read)
- Modify: `src/main/java/com/citymodeler/matsim/models/transit/TransitStopFacility.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/io/TransitScheduleXmlWriter.java`, `TransitScheduleXmlReader.java` (parentId serialization)
- Test: `src/test/java/com/citymodeler/matsim/models/transit/TransitContainerGuardsTest.java`
- Test: `src/test/java/com/citymodeler/matsim/models/network/LinkEmptyModesRoundTripTest.java`

**Interfaces:**
- Produces: `addStopFacility`/`addTransitLine`/`addRoute`/`addDeparture` throw `IllegalArgumentException` on duplicate ids.
- Produces: `Link` with empty `allowedModes` stays empty through construction, `setAllowedModes`, and XML round trip; writer always emits `modes` (empty attribute allowed).
- Produces: `TransitStopFacility.getParentId()` / `setParentId(Id<TransitStopFacility>)`; writer emits `parentId` attribute when set; reader parses it.
- Phase 2 child-stop logic consumes parentId; Phase 2C consumes empty-modes semantics.

- [ ] **Step 1: Write failing tests**

- Duplicate guards: each container throws on second add with same id (including across lines for departures within one route).
- Empty modes: `new Link(id, from, to, len, cap, fs, lanes, new LinkedHashSet<>())` keeps `getAllowedModes()` empty; `setAllowedModes(empty)` keeps empty; write → read preserves empty (assert `modes=""` present in XML).
- Child parent: `setParentId`, writer emits `parentId="..."`, reader restores.

- [ ] **Step 2: Run failing tests**

Run: `mvn -Dtest=TransitContainerGuardsTest,LinkEmptyModesRoundTripTest,NetworkModelTest,TransitModelTest test`
Expected: new tests fail; existing model tests may fail where they relied on silent overwrite or car-defaulting — fix those call sites in the same task.

- [ ] **Step 3: Implement**

- Replace bare `put` with contains-check + `IllegalArgumentException` in the four add methods.
- `Link`: remove the empty→car defaulting in constructor and `setAllowedModes` (keep `null` → empty).
- `NetworkXmlWriter`: always write `modes` attribute (possibly empty string).
- `NetworkXmlReader`: parse empty/absent `modes` into an empty set (no defaulting).
- `TransitStopFacility`: add nullable `parentId` field with getter/setter; writer/reader support.

- [ ] **Step 4: Run tests**

Run: `mvn -Dtest=TransitContainerGuardsTest,LinkEmptyModesRoundTripTest,NetworkModelTest,TransitModelTest,NetworkXmlTest,TransitScheduleXmlTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/transit src/main/java/com/citymodeler/matsim/models/network src/main/java/com/citymodeler/matsim/models/io src/test/java/com/citymodeler/matsim/models/transit src/test/java/com/citymodeler/matsim/models/network
git commit -m "feat: container guards, mode preservation, child-stop parent"
```

---

### Task 5: Deterministic Id Codec And Validator Options

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/api/MatsimIds.java`
- Modify: `src/main/java/com/citymodeler/matsim/models/validation/NetworkValidator.java`
- Test: `src/test/java/com/citymodeler/matsim/models/api/MatsimIdsTest.java`
- Modify: `src/test/java/com/citymodeler/matsim/models/validation/NetworkValidatorTest.java`

**Interfaces:**
- Produces: `MatsimIds.compose(String delimiter, String... parts)` with `escape(String)` doubling any occurrence of the delimiter inside a part; `compose` rejects null/blank parts; round trip `MatsimIds.decompose(String, String)`.
- Produces: `NetworkValidator` options object: `allowSelfLoopPrefix(String prefix)` (downgrades self-loop ERROR/WARNING to INFO for ids with the prefix), `maxLinkLengthMeters` (optional check), `requireModes(Set<String>)` (optional coverage check).
- Phase 1/2 id composition and plausibility checks consume these.

- [ ] **Step 1: Write failing tests**

- Codec: compose/escape/decompose round trips; id containing `:` survives; id containing the child convention `.link:` survives; blank part rejected.
- Validator: self-loop with `pt_` prefix → INFO only when option set; `requireModes` catches a mode-less network; `maxLinkLengthMeters` flags a fabricated long link.

- [ ] **Step 2: Run failing tests**

Run: `mvn -Dtest=MatsimIdsTest,NetworkValidatorTest test`
Expected: compile failure (codec) / new validator cases fail.

- [ ] **Step 3: Implement**

- `MatsimIds` static utility.
- Validator options with defaults preserving current behavior (no option → no change).

- [ ] **Step 4: Run tests**

Run: `mvn -Dtest=MatsimIdsTest,NetworkValidatorTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/api src/main/java/com/citymodeler/matsim/models/validation src/test/java/com/citymodeler/matsim/models/api src/test/java/com/citymodeler/matsim/models/validation
git commit -m "feat: deterministic id codec and validator options"
```

---

### Task 6: Streaming StAX Writers For Large Artifacts

**Files:**
- Create: `src/main/java/com/citymodeler/matsim/models/io/StreamingNetworkWriter.java`
- Create: `src/main/java/com/citymodeler/matsim/models/io/StreamingTransitScheduleWriter.java`
- Test: `src/test/java/com/citymodeler/matsim/models/io/StreamingWritersTest.java`

**Interfaces:**
- Produces: `StreamingNetworkWriter.write(Network, Path)` and `StreamingTransitScheduleWriter.write(TransitSchedule, Path)` producing XML byte-equivalent in content (element order and attributes) to the DOM writers, including attributes blocks, `modes` always emitted, `linkRefId`/child `transportMode`/`<route>` for schedules, and `capperiod` placeholder support on `<links>` via constructor option.
- Phase 1/2 large outputs use these writers.

- [ ] **Step 1: Write failing tests**

- Write a medium synthetic network (e.g., 500 nodes / 1000 links built in a loop) with both writers; parse both outputs with the readers; assert equal model contents; assert the streaming output for a small fixture matches the DOM writer output modulo whitespace.
- Schedule fixture with a route sequence (from Task 2 model) round-trips through the streaming writer.

- [ ] **Step 2: Run failing tests**

Run: `mvn -Dtest=StreamingWritersTest test`
Expected: compile failure.

- [ ] **Step 3: Implement**

- StAX `XMLStreamWriter` writers with the same element/attribute emission order as the DOM writers; reuse `XmlSupport` escaping/open helpers where possible; gzip by path suffix like existing IO.

- [ ] **Step 4: Run tests**

Run: `mvn -Dtest=StreamingWritersTest,NetworkXmlTest,TransitScheduleXmlTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/citymodeler/matsim/models/io src/test/java/com/citymodeler/matsim/models/io
git commit -m "feat: streaming StAX writers for large artifacts"
```

---

## Final Phase 0 Verification

- [ ] Run: `mvn clean verify -B` — PASS.
- [ ] Run: `grep -R "org\.matsim" src/main/java` — the only match is `MATSIM_DISALLOWED_NEXT_LINKS_CLASS_HINT` in `XmlSupport`.
- [ ] Confirm `TransitScheduleXmlTest` asserts `linkRefId`, child `transportMode`, and `<route>` emission.
- [ ] Confirm `VehiclesXmlTest` round-trips `vehicleType` with capacities.
- [ ] Confirm duplicate-add throws and empty-modes survive a network round trip.
- [ ] Confirm `ScenarioModelTest` covers the vehicles field and validator cross-check.

## Known Execution Note

This workspace previously reported `mvn: command not found`. If Maven is still unavailable, execute tests in an environment with Maven installed before claiming completion.