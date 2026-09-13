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
- Via-way turn restriction enforcement (separate Phase B follow-up after this branch).
- New mapping features beyond what the phase-2 spec already requires.

## Clean-room

Same rules as the merged network work: behavior derived from this repo's specs, OSM semantics,
GTFS spec, and published MATSim XSDs. pt2MATSim is a black-box oracle only. No external source,
bytecode, or config is copied or vendored.

## Part 1 — Lane decomposition

### Inputs
- An emitted directed `Link` and its source OSM way tags (`lanes`, `lanes:forward`, `lanes:backward`,
  `lanes:both_ways`, `turn:lanes`, `turn:lanes:forward`, `turn:lanes:backward`,
  `turn:lanes:both_ways`, `bus:lanes`, `psv:lanes`, etc.).
- Whether each directed link's source way is `oneway` (affects `lanes` interpretation).
- The junction's outgoing links (from the network at the link's to-node) and their geometry.
- The link's own geometry (for bearing).

### 1a. Directional lane-count resolution (authoritative rules)

`lanes=*` is the **total across both directions**, not per direction. Copying `lanes=4` into both
directed links as 4+4 is forbidden. Resolution for a directed link (its travel direction = forward
when the link follows the way's node order, else backward):

**Direction source is shared with the network.** Whether a link is one-way is determined from the
same resolved source as network construction — `OsmModeAccessResolver.resolve(way, candidateModes,
rule.defaultOneway())`, with `oneway = !(forwardAllowed && backwardAllowed)` — not a second
hand-written `oneway=*` parser. This guarantees a way emitted one-way in `network.xml` (including
rule-default one-way such as `motorway`) is treated one-way when generating `laneDefinitions.xml`.

1. **`lanes:forward` / `lanes:backward` present** → authoritative for that direction. Use directly.
2. **`oneway=yes`** → `lanes=*` is the travelled direction's count (the wiki's one-way assumption);
   use `lanes` directly for the single directed link. `lanes:both_ways` is recorded as provenance
   only (see step 4); it is not added to the count.
3. **Bidirectional, `lanes` present, no directional tags**:
   - subtract `lanes:both_ways` (default 0) to get the directional-total `T = lanes - both_ways`;
   - **`T` even** → apply the documented OSM even-split assumption: each direction gets `T/2`
     (confidence `wiki-default-even-split`). This is the only split we apply automatically.
   - **`T` odd** (or `T < 1`) → the split is **not determinable**. Do **not** invent a direction
     count: emit `round(T / 2)` physical lane objects (minimum 1) with confidence
     `undetermined-split`, preserve `T` as `osm:lanes.total`, and record a structured issue.
   - **Single directional tag with `lanes` present** (e.g. `lanes=4`, `lanes:forward=3`): derive the
     missing direction as `other = total - known - both_ways`. If `other >= 1` use it (confidence
     `present`); otherwise the tags are internally inconsistent — record an `inconsistent-lane-tags`
     issue and fall back to step 3's total-split/undetermined handling for that direction.
4. **`lanes:both_ways=N`** → those lanes are usable in both directions (centre/passing turn lanes).
   A genuine shared/reversible centre-lane model does not exist yet, so they are **preserved as
   provenance/diagnostic only** (`osm:lanes.bothWays=N` on the lane) and are **not** duplicated into
   each direction: the per-direction count is derived from `lanes - lanes:both_ways` (step 3) or the
   explicit directional tag. This keeps the sum of directed lane objects equal to the declared
   physical total. Their `turn:lanes:both_ways` tokens are recorded where present but not applied as
   per-direction movements until a shared-lane model exists.
5. **No lane tags at all** → the base network's per-direction count, `rule.lanesPerDirection()`
   (e.g. 2 for `primary`, 3 for `motorway`), as that many physical lane objects with confidence
   `absent`; this guarantees `laneDefinitions.xml` agrees with `network.xml` `permlanes`. Not
   fabricated beyond the network's own default.

Malformed lane counts (`lanes=0`, `-1`, `1.5`, `none`, blank) are ignored with an issue; never
coerced into a positive integer.

### 1b. `turn:lanes` token semantics

Applied per direction (`turn:lanes:forward`/`:backward`, else `turn:lanes` for the whole carriageway
only when the way is bidirectional with an even split and no directional turn tag — otherwise a
whole-carriageway tag on a bidirectional way is `ambiguous` and not applied). Lanes are ordered
left→right in the **direction of travel** of the directed link.

Deterministic handling per token (a lane cell may contain several indications joined by `;`):

| token | meaning | resolution |
|---|---|---|
| `left` | left turn | outgoing link whose turn type is `left` |
| `slight_left` | slight left | outgoing `slight_left` |
| `sharp_left` | sharp left | outgoing `sharp_left` (fallback to `left` if only one left exists, flagged) |
| `through` | straight | outgoing `through` |
| `right` | right turn | outgoing `right` |
| `slight_right` | slight right | outgoing `slight_right` |
| `sharp_right` | sharp right | outgoing `sharp_right` (fallback to `right` if only one right exists, flagged) |
| `reverse` | U-turn | outgoing `reverse` if one exists, else unresolved (flag) |
| `merge_to_left` | merge left | lane ends; attribute `osm:lane.merge=left`; schema-mandatory `leadsTo` = all geometrically-available outgoing links, confidence `merge` |
| `merge_to_right` | merge right | lane ends; `osm:lane.merge=right`; `leadsTo` = all outgoing, confidence `merge` |
| `none` | no marked indication | lane unrestricted by turn marking → all geometrically-available outgoing links, confidence `none-observed` |
| empty cell | missing indication | eligibility unknown (not observed): all geometrically-available outgoing links, confidence `absent`, issue |
| multiple via `;` | e.g. `left;through` | union of each token's resolution; shared lane (see 1c) |
| unknown token | e.g. `left_turn` or a misspelling | keep raw token, **no** guessed movement: all geometrically-available outgoing links, confidence `unsupported`, issue |

Because the published `laneDefinitions_v2.0` XSD makes `<leadsTo>` mandatory, every lane must carry
at least one `toLink`. When turn evidence is missing or unsupported we therefore emit **all
geometrically-available outgoing links** as a conventional "unrestricted" lane and record *why* in the
confidence/provenance attribute, so the fallback is never mistaken for observed eligibility.

Turn type of each outgoing link is inferred from the bearing delta between the incoming link's final
segment heading and the outgoing link's initial segment heading, bucketed into the classes above.

### 1c. Lane-count / `turn:lanes` reconciliation

Never silently “trust `turn:lanes`”. When token count ≠ resolved lane count:

1. preserve **both** source values as attributes (`osm:lanes.count`, `osm:turnLanes.count`);
2. record one structured mismatch issue (code `lane-count-mismatch`);
3. apply a **conservative reconciliation**:
   - if the directed lane count is `undetermined-split` or `absent`, adopt the `turn:lanes` token
     count as the authoritative lane count (the explicit per-lane tag is the better evidence),
     confidence `turn-lanes-authoritative`;
   - otherwise keep the directional lane count, and align tokens by position; if tokens are fewer,
     the trailing lanes get no turn indication (`absent`); if more, drop the extra tokens (with an
     issue). **Never** fabricate lane count from a malformed tag.

The 0-based left→right lane position is a separate attribute `osm:lane.index` (not a count).

### 1d. Absent `turn:lanes`

When no turn tagging exists for the direction, each lane is created with `leadsTo` = all
geometrically-available outgoing links (the schema-mandatory "unrestricted" fallback) and confidence
`absent`. This means movement eligibility is **unknown / unrestricted by available lane-tag
evidence** — it does **not** mean every movement was observed as allowed. The distinction is carried
in the confidence/provenance attribute so downstream signal work can choose conservative defaults
rather than assume observed eligibility.

### 1e. Capacity (attached to the lane, not double-counted)

- Capacity is fundamentally a property of the **lane**: lane capacity = the link's rule
  `capacityPerLane` (our own table; not invented per-lane).
- The link's capacity remains the sum over its lanes (unchanged behavior). **Lane capacity is the
  source of truth; movement capacity is a derived view.**
- A lane is classified by how many movements it serves:
  - **exclusive** (serves exactly one movement): its full lane capacity is *dedicated* to that
    movement and may be added to that movement's dedicated total.
  - **shared** (`left;through`, or `none` spanning several movements): its capacity is an **upper
    bound per served movement, not an allocation**. Each such movement is tagged
    `capacity.shared=true`.
- **Invariant:** dedicated contributions across movements sum to ≤ the lane's capacity; shared-lane
  contributions are flagged and are **never added** as if simultaneously available. If an additive,
  simultaneously-available movement-capacity figure is ever required, it needs an explicit allocation
  model (green-time split, demand share, …) which is **out of scope** here. The design will not emit
  a summed movement capacity that implies shared lanes are simultaneously fully usable.
- Tests assert: exclusive lane contributes fully; a shared `left;through` lane yields per-movement
  values marked `shared` whose sum is not presented as an additive capacity, and the dedicated
  (exclusive) sum over the lane never exceeds the lane's physical capacity.

### 1f. Model and IDs
- Populate the existing `Lanes` / `LanesToLinkAssignment` / `Lane` model.
- `Lane.id = <linkId>_l<index>`, index left→right in direction of travel (deterministic).
- Lane attributes record provenance: source tag key+value, raw token, and confidence
  (`present`, `wiki-default-even-split`, `undetermined-split`, `turn-lanes-authoritative`,
  `absent`, `none-observed`, `unsupported`, `partial`, `merge`), plus `osm:lane.merge` for merge cells.

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

- `leadsTo` is an `xs:choice`: it contains **either** `toLink` **or** `toLane`, never both. Emit the
  `toLink` branch when the lane has any `toLink` ids, else the `toLane` branch. If a lane carries
  both (model permits it), emit `toLink` and preserve the dropped `toLane` ids in the lane attribute
  `osm:lane.toLaneIds` **and rehydrate that attribute back into the `toLaneIds` model list on read**,
  so the mixed-lane relationship is reversible through a write/read round-trip rather than silently
  degraded to metadata.
- `leadsTo` is **mandatory** in the published XSD: emit all geometrically-available outgoing links
  (conventionally the "unrestricted" lane) with a provenance attribute recording the evidence
  (`observed`, `none-observed`, `absent`, `unsupported`). A lane with neither `toLink` nor `toLane`
  is invalid; the writer rejects it rather than emitting `<leadsTo/>`.
- `alignment` is **mandatory** (`xs:int`): normalize to an integer. Parse the lane's alignment
  string; if it is null, blank, or non-integer, emit `0` and tag `osm:lane.alignmentProvenance=default`
  (plus `osm:lane.alignmentRaw=<original>` when a non-numeric value was supplied).
- The published `laneDefinitions` root has **no attributes**; the writer emits none (the reader keeps
  a lenient read of legacy root attributes for tolerance only).
- `capacity`, `startsAt`, `representedLanes` are optional and emitted only when we have a value;
  otherwise omitted (schema defaults), so absence is explicit rather than fabricated.
- Child element order must follow the XSD: `leadsTo, representedLanes, capacity, startsAt,
  alignment, attributes`.
- The **production** schema resource used by `LanesXmlReader(true)` is the published v2.0 schema
  (vendored under `src/main/resources/schemas/v2/` with the same third-party attribution as
  `vehicleDefinitions_v2.0.xsd`), not an `xs:anyType` stub, so `validateSchema=true` genuinely means
  laneDefinitions v2.0 validation. The historic `<lanes>` dialect is read leniently only when
  validation is off.
- Keep `LanesXmlReader` able to read the old simplified fixture for backward compatibility, or
  migrate that fixture — decide during implementation based on test impact.

## Part 3 — Bundle output

`OsmGtfsBundleRunner` additionally writes `laneDefinitions.xml` (constant `LANE_DEFINITIONS_FILE`)
and exposes its path in `BundleResult`. Emitted **always**. Every **eligible (non-terminal) link**
gets a lane assignment; a terminal/dead-end link (no outgoing links, which would require an invalid
empty `leadsTo`) is skipped with a `lane-no-outgoing` diagnostic. When OSM has no lane data the file
still contains one lane per eligible link at the network's own per-direction lane count.

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
  plain GeoJSON text), for opening in GIS. GeoJSON coordinates are **WGS84 lon/lat** by contract:
  where the facility still carries `gtfs:lon`/`gtfs:lat` use those; otherwise inverse-project the
  network-CRS coordinate back to WGS84. Never write projected metres into a `.geojson` as if they
  were degrees.
- **Numeric assertions**: where GTFS provides a stop name and our link carries `osm:name` /
  `osm:sourceNames`, assert the chosen link's road name relates to the stop name; assert chosen
  distance is under the configured threshold.

Black-box comparison: compare mapped network/schedule against pt2MATSim output on the same
input (counts, route continuity), recorded in `docs/pt2matsim-blackbox-comparison.md`.

## Part 5 — Via-way restrictions (NOT in this branch)

Via-way restriction enforcement is **out of scope for this branch**. It is preserved topologically
today (all via-chain nodes are kept) but not enforced at routing; that remains a separate Phase B
follow-up after lanes + mapping hardening. This branch does not change via-way behavior.

## Testing strategy

Test-first for each unit. New/changed tests:
- **Directional lane count:** `lanes=2` (even → 1+1), `lanes=3` (odd → undetermined split, issue, no
  invented count), `lanes=4` (even → 2+2), explicit `lanes:forward`/`:backward` (authoritative),
  `lanes:both_ways` (provenance only; not duplicated, per-direction count excludes it), a single
  directional tag + total (missing direction = `total - known - both_ways`, inconsistency flagged),
  oneway with `lanes`, rule-default oneway (motorway), absent tags (network's `lanesPerDirection`),
  malformed counts (`0`, `-1`, `1.5`).
- **`turn:lanes` tokens:** every token in the table above; `left;through` shared lane →
  two `leadsTo` links; empty cell; unknown token preserved with `unsupported` confidence; `none`.
- **Reconciliation:** token count vs lane count mismatch — undetermined/absent → adopt token count;
  otherwise keep lane count and align; both source values preserved; structured issue emitted.
- **Capacity:** exclusive lane contributes fully; shared `left;through` lane does **not** yield two
  movement capacities each equal to the full lane capacity (sum ≤ lane capacity); link capacity
  unchanged.
- `laneDefinitions`: round-trip; XSD-valid; omitted-when-unknown fields.
- Bundle: `laneDefinitions.xml` present in output and `BundleResult`.
- Mapping: the seven plan tests above + GeoJSON + numeric assertion.
- Full gate: `mvn -o -B clean verify` green (tests, checkstyle, SpotBugs).

## Risks / decisions

- **Directional split on bidirectional ways** is the highest-risk area: only the OSM-documented
  even-split is applied automatically; odd totals stay undetermined with a visible issue. No count is
  ever fabricated.
- **Geometry-derived turn type** can misclassify on complex junctions; mitigated by keeping the raw
  token and confidence attribute, and by only asserting movement mapping where it is unambiguous.
- **`turn:lanes` coverage** in real data is partial; absent lanes are explicitly unknown, not
  observed-unrestricted.
- **Backward compatibility** of the lane IO format: existing simplified fixture/tests may need
  migration; decided during implementation.
- **No timing data**: not in scope here; user supplies controller/timing separately.
