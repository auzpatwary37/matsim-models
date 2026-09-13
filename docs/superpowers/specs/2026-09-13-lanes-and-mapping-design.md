# Lanes and Mapping Hardening — Design

Date: 2026-09-13
Branch: `feat/lanes-and-mapping` (off `main` @ `5711a00`)

## Purpose

The bundle runner currently emits an OSM-derived `network.xml` (with turn restrictions), a
GTFS-derived `transitSchedule.xml` + `vehicleDefinitions.xml`, and their mapped counterparts. Two
gaps remain for a signal-ready, routing-ready scenario:

1. **No lane file.** OSM `turn:lanes` is preserved only as a raw tag; no `laneDefinitions` file is
   produced, and our existing `LanesXmlWriter` emits a simplified, non-standard format.
2. **Mapping is under-verified.** The phase-2 plan lists nine verification tests; only one exists.

This design covers lane production and mapping hardening. Signal control files (`signalSystems`,
`signalGroups`, `signalControl`) are **out of scope** — that is separate work with user-supplied
controller/timing data.

## Scope

In scope:
- Decompose OSM lane tags into a real `Lanes` model (per-lane `leadsTo` outgoing links, capacity).
- Emit/read schema-correct MATSim `laneDefinitions.xml`.
- Wire `laneDefinitions.xml` into the bundle output.
- Harden and verify transit mapping: add the phase-2 plan's missing tests, fix what they surface.
- GeoJSON export of stop→link assignments for physical spot-checks.
- Numeric stop→link assertions using saved OSM road names (`osm:name`, `osm:sourceNames`) + distance.
- Black-box comparison against pt2MATSim output (mapped schedule / network), as already done for the
  base network.

Out of scope:
- Signal system/group/control files and timing.
- Via-way turn restriction enforcement (separate workstream, done last).
- New mapping features beyond what the phase-2 spec already requires.

## Clean-room

Same rules as the merged network work: behavior derived from this repo's specs, OSM semantics,
GTFS spec, and published MATSim XSDs. pt2MATSim is a black-box oracle only. No external source,
bytecode, or config is copied or vendored.

## Part 1 — Lane decomposition

### Inputs
- An emitted directed `Link` and its source OSM way tags (`lanes`, `lanes:forward`, `lanes:backward`,
  `turn:lanes`, `turn:lanes:forward`, `turn:lanes:backward`, `bus:lanes`, `psv:lanes`, etc.).
- The junction's outgoing links (from the network at the link's to-node) and their geometry.
- The link's own geometry (for bearing).

### Algorithm
1. Choose the applicable lane-count tag for the link's travel direction (`lanes:forward`/`backward`,
   else `lanes`, else 1).
2. Choose the applicable `turn:lanes` tag for the direction. Split on `|` into ordered lane tokens.
3. For each token, map turn indications to a set of outgoing link IDs:
   - infer each outgoing link's turn type from the bearing delta between the incoming link's final
     heading and the outgoing link's initial heading (through / slight_left / left / slight_right /
     right / reverse);
   - `through` → the through outgoing link; `left` → the left one; `right` → the right one;
     `slight_*`/`merge_*` → the nearest matching outgoing;
   - a lane with several tokens (e.g. `left;through`) or a single token covering several
     possibilities yields **multiple** `leadsTo` links.
4. If `turn:lanes` is absent: create one lane per lane-count with **no** movement restriction
   (eligibility unknown — not invented).
5. If `turn:lanes` token count ≠ lane count, trust `turn:lanes` and record an issue.
6. Unsupported/ambiguous tokens: keep the raw token as a lane attribute and record an issue; do not
   fabricate eligibility.

### Capacity
- Lane capacity = the link's rule `capacityPerLane` (our own table; not invented per-lane).
- Movement capacity = sum of capacities of lanes whose `leadsTo` contains that movement's outgoing
  link (shared lanes count for each movement they serve, so capacity is shared not double-counted).
- Link capacity unchanged (= sum of lanes × per-lane capacity).

### Model and IDs
- Populate the existing `Lanes` / `LanesToLinkAssignment` / `Lane` model.
- `Lane.id = <linkId>_l<index>`, index left→right (deterministic).
- Lane attributes record provenance: source tag, raw token, confidence (present / absent /
  ambiguous / partial).

## Part 2 — `laneDefinitions` IO

Replace the simplified writer/reader output with the published `laneDefinitions_v2.0` shape:

```
<laneDefinitions>
  <lanesToLinkAssignment linkIdRef="link">
    <lane id="link_l0">
      <leadsTo><toLink refId="outA"/><toLink refId="outB"/></leadsTo>
      <capacity vehiclesPerHour="1800"/>
      <startsAt meterFromLinkEnd="45"/>
      <alignment>0</alignment>
    </lane>
    ...
  </lanesToLinkAssignment>
</laneDefinitions>
```

- `leadsTo` may contain `toLink` and/or `toLane` (lane-to-lane across the junction when known).
- `capacity`, `startsAt`, `alignment` are emitted only when we have a value; otherwise omitted
  (schema defaults), so absence is explicit rather than fabricated.
- Keep `LanesXmlReader` able to read the old simplified fixture for backward compatibility, or
  migrate that fixture — decide during implementation based on test impact.

## Part 3 — Bundle output

`OsmGtfsBundleRunner` additionally writes `laneDefinitions.xml` (constant `LANE_DEFINITIONS_FILE`)
and exposes its path in `BundleResult`. Emitted **always** so downstream always has the file; when OSM
has no lane data the file still contains one unrestricted lane per link.

## Part 4 — Mapping hardening

Add the phase-2 plan's missing verification tests and fix what they surface:
- `MappedRouteContinuityTest` — network routes are continuous and single-mode.
- `TurnRestrictionRoutingTest` — restricted movements are honored in mapped routes.
- `ArtificialLinkTest` — artificial connectors/loops have valid fields, order-independent.
- `MappedChildStopsTest` — child stops carry parentId + link reference.
- `DeterministicOutputTest` — byte-identical output under shuffled input.
- `GtfsScheduleRoundTripTest` — schedule round-trips through writer/reader.
- `VehicleDefinitionsTest` — every departure's vehicleRefId resolves; every vehicle references a type.

Physical verification (stop → link):
- **GeoJSON export** of mapped stops, their chosen links, and candidate links (no new dependency;
  plain GeoJSON text), for opening in GIS.
- **Numeric assertions**: where GTFS provides a stop name and our link carries `osm:name` /
  `osm:sourceNames`, assert the chosen link's road name relates to the stop name; assert chosen
  distance is under the configured threshold.

Black-box comparison: compare mapped network/schedule against pt2MATSim output on the same
input (counts, route continuity), recorded in `docs/pt2matsim-blackbox-comparison.md`.

## Part 5 — Via-way restrictions (last)

Enforce multi-link (`via=way`) turn restrictions end-to-end so mapped routing honors them; today they
are preserved topologically but only counted. Covered by its own tests. Done last because signal
groups and routing both depend on correct movement legality.

## Testing strategy

Test-first for each unit. New/changed tests:
- Lane decomposition: lane count/order; left→{left,through} and right→{right,through} map to the
  correct outgoing IDs; dedicated bus/psv lane; absent vs ambiguous vs partial; capacity per movement;
  determinism.
- `laneDefinitions`: round-trip; XSD-valid; omitted-when-unknown fields.
- Bundle: `laneDefinitions.xml` present in output and `BundleResult`.
- Mapping: the seven plan tests above + GeoJSON + numeric assertion.
- Full gate: `mvn -o -B clean verify` green (tests, checkstyle, SpotBugs).

## Risks / decisions

- **Geometry-derived turn type** can misclassify on complex junctions; mitigated by keeping the raw
  token and confidence attribute, and by only asserting movement mapping where it is unambiguous.
- **`turn:lanes` coverage** in real data is partial; absent lanes stay explicitly unrestricted.
- **Backward compatibility** of the lane IO format: existing simplified fixture/tests may need
  migration; decided during implementation.
- **No timing data**: not in scope here; user supplies controller/timing separately.
