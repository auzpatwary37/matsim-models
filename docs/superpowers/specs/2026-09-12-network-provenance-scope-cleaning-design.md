# Network Provenance, Scope, and Routability Cleaning — Design

**Status:** approved in brainstorming (2026-09-12). Independently derived from this repo's specs
(`2026-09-08-gtfs-transit-mapping-clean-room-design.md`,
`2026-09-10-signal-ready-network-clean-room-spec.md`) and OSM semantics. External networks are used
only as black-box output for measurement (see `docs/pt2matsim-blackbox-comparison.md`); no external
source, bytecode, or configuration is used to derive behavior.

## Problem

The topology-contraction engine produces a compact, cross-way network, but three gaps remain:

1. **Provenance is lost and misleading.** A merged link records only the *first* source way id and
   that way's tags/name. The geometry sidecar (full polyline) exists in memory but is never written,
   so it does not survive a `network.xml` round-trip. There is no record of all source way ids or the
   ordered source OSM node ids. Example: a 9,729 m, 269-segment merged link is attributed to way
   `1441606921` ("An der Waark") whose physical extent is 172 m over 4 nodes.
2. **Way-class scope is hard-coded.** The default admits every rule, including `highway=service`
   aisles, with no way to configure a narrower road-network scope. `highway=service` alone accounts
   for ~55,900 links / ~5,000 km in Luxembourg — a deliberate default, but not tunable.
3. **Cleaning does not guarantee routability.** The existing `OsmNetworkCleaner` removes only
   components smaller than 3 directed links with no transit mode, is not per-mode, and is off by
   default. Default output can contain islands, one-way sinks, and dead-ends. A routed simulation
   requires each configured routable mode to be strongly connected.

## Decisions (binding)

- **Component rule (routable modes):** for each configured routable mode (default `car`, `bus`), keep
  only the largest **strongly connected** component — every surviving link is reachable from and can
  return to every other link for that mode. This is stronger than the older spec's "weakly connected"
  wording, which does not guarantee routing; the stronger rule is adopted deliberately.
- **Non-routable modes keep sinks/sources.** Rail/tram and other transit modes are oneway or
  spur-shaped by nature; applying strong connectivity to them would delete real infrastructure. They
  are therefore not connectivity-cleaned.
- **Cleaning runs by default** as a normative stage of both `build()` and signal-ready `simplify()`.
- **Quarantine, never silent deletion:** removed components are retained in
  `OsmNetworkBuildResult.quarantinedComponents()` with issues carrying source OSM ids.
- **Scope is configurable**, default unchanged (service included, per the original spec's default-class
  list); a `compactRoadNetworkConfig()` excludes service for a compact road network.
- **Geometry encoding:** WKT `LINESTRING (x y, x y, ...)` in the network's projected coordinates.
- **Names:** `osm:name` = representative name; `osm:sourceNames` emitted only when the chain spans
  more than one distinct name.

## Part A — Provenance round-trip

The engine already computes the full polyline and the ordered source segments. Persist per emitted
link, as link attributes (the XSD is `xs:anyType`; no `Network`/`Link` shape change):

| attribute | value |
|---|---|
| `osm:geometry` | WKT `LINESTRING (x y, x y, ...)`, full polyline, projected coords |
| `osm:sourceWays` | all source OSM way ids, deduped, canonical order, comma-separated |
| `osm:sourceNodes` | all source OSM node ids, travel order, comma-separated |
| `osm:name` | representative name (from the canonical first source way) |
| `osm:sourceNames` | distinct names, canonical order, comma-separated — only when >1 distinct |

`osm:wayId` (first source way) is retained for back-compat. Links stay merged; `linkId`, endpoints,
and topology are unchanged.

**Reader:** `NetworkXmlReader` round-trips arbitrary attributes generically; add a small typed helper
to decode `osm:geometry` into points and expose source lists, plus an explicit writer→reader
round-trip test.

**Writer:** set the attributes at emit time in `OsmTopologyBuilder.emitLink`; both
`StreamingNetworkWriter` and `NetworkXmlWriter` already emit all attributes.

## Part B — Way-class scope

- Add a configurable excluded-class set to `OsmNetworkBuildConfig`, threaded through the
  constructors/factories. Default unchanged (nothing excluded).
- Add `compactRoadNetworkConfig()` that excludes `highway=service`, producing a compact road network
  without driveway/parking aisles.
- Re-measure and record the delta. Do not change the default.

## Part C — Routability cleaning

Replace the weak `OsmNetworkCleaner` with a mode-aware cleaner:

1. **Invalid-element removal:** drop links with missing endpoints, non-finite metrics, or non-positive
   length, except artificial connectors whose id starts with `pt_`. Emit issues.
2. **Routable-subnetwork reduction:** only the modes declared **routable** (default `car`, `bus`) are
   reduced, each to its largest **strongly** connected component. A link is removed only when it is
   outside the largest SCC of **every** routable mode it permits.
3. **Non-routable modes are NOT connectivity-cleaned.** Rail, tram, and any other non-routable mode
   keep their sinks and sources: a oneway transit line has no directed return path, so
   strong-connectivity must not be applied to it.
4. **Quarantine:** removed link groups retained on the result; issues carry source OSM way ids.
5. **Sidecar compaction:** linkRefs, linkIdsByOsmWayId, geometry, hints and classification compacted to
   surviving ids. Deterministic.

**Turn-restriction awareness:** restrictions can make a link look disconnected when a forbidden turn
is the only path. For this pass, cleaning operates on the *plain* directed subgraph, then a validation
stage drops any stored `disallowedNextLinks` sequence whose links no longer all exist / remain
contiguous (already a spec requirement). Turn-restriction-*aware* cleaning is **deferred**.

**Pipeline order** (spec §Network Cleaning), implemented in `OsmTopologyBuilder.buildCore`:
select/contract → normalize metrics → component cleaning + quarantine + sidecar compaction →
(build) → restriction translation/validation against final ids (signal-ready path).

**Config:** `cleanupIsolatedComponents` toggles the cleaner (default **true** for
`build()`/`simplify()`); `routableModes` selects which subnetworks are reduced (default `car`, `bus`).
The old `<3-link non-transit` behavior is removed.

## Road-network mode and direction model

Derived from OSM semantics and our own spec:

- **Buses on car roads.** Our spec treats a `car` link as re-checking bus/PSV legality at routing time
  and materializes the transit mode per route. To make the bus routable subnetwork explicit in the
  base network (so cleaning and routing can see it), `addBusToCarRoads` adds `bus` to every link that
  allows `car` (default true). This does not change car topology.
- **Direction defaults from OSM.** A way's travel direction is oneway only when OSM says so
  (`highway` motorway, `junction=roundabout`, or an explicit `oneway` tag). `OsmWayRule.defaultOneway`
  supplies a per-class fallback and was previously dead code; it is now honored. Rail mainline track is
  bidirectional (OSM routes both directions over the same track); street-running tram is modeled as a
  directional track. These are independent OSM modeling decisions, not taken from an external tool.
- **Bounded road-link length.** `maxContractedLinkLengthMeters` (default 0 = no bound) additionally
  retains a routing node when dissolving it would make a **car** link exceed the bound, so a road with
  almost no junctions does not collapse into one implausibly long link. Rail/tram are never
  length-split. `compactRoadNetworkConfig()` sets 500 m.

## Testing

- **Provenance:** multi-way merged link has all source ways/nodes/names; geometry WKT decodes to the
  same vertices and length; single-way link has one way/name and no `osm:sourceNames`; writer→reader
  preserves merged ids, geometry, source ways/nodes, names; names containing commas round-trip as
  distinct values (reversible list codec).
- **Scope:** default admits service; compact preset excludes it; service way dropped under compact preset.
- **Cleaning:** a disconnected car-only island is removed and quarantined; a one-way car sink is
  removed; an isolated routable stub is removed; the largest component survives; non-routable modes
  keep sinks/sources; zero-length removed except `pt_`; sidecars reference only surviving ids.
- **Direction:** rail bidirectional, tram oneway, oneway car roads single directed link.
- **Access:** `bus=no`/`psv=no` on a car road keeps bus out (explicit tags beat `addBusToCarRoads`);
  `motor_vehicle=no + bus=yes` keeps a bus-only link; directional `motor_vehicle:forward=no` removes
  both car and bus; `access=no + bus=designated` keeps bus only.
- **Turn restrictions:** `except=psv` exempts bus/pt, `except=motorcar` exempts car, semicolon lists
  exempt all mapped modes; a via-way restriction is counted as unimplemented rather than silently
  ignored.
- **Contraction boundary:** a change in `turn:lanes` (or other lane-semantic tags) between adjacent
  ways keeps the intermediate node.
- **Link identity:** a way that doubles back re-traverses the same directed span and is collapsed with
  a warning; distinct chains between the same endpoints are disambiguated by a stable node-sequence
  hash and both survive.
- **Invariance:** on a connected fixture, cleaning is a no-op; `oneWayAndSplitWayProduceIdenticalTopology`
  still passes.
- **Full gate:** `mvn -o -B clean verify` green.

## Compatibility / risk

- Existing small fixtures that are single-island or single-link are cleaned. Tests build
  strongly-connected fixtures or opt out via config.
- Cleaning on by default is a deliberate output change; the default scope (service included) is NOT changed.
- Bus admission happens in access resolution, not as a post-hoc `car`→`bus` clone, so explicit OSM
  access tags always retain final authority.
- Clean-room: no external source, bytecode, or configuration is read; external outputs are used only
  as black-box measurements in `docs/pt2matsim-blackbox-comparison.md`.

## Faithful import vs policy presets

`faithfulImportConfig()` performs a literal import: no connectivity cleaning, no length cap, no bus
expansion, and no parallel-track collapse. `defaultConfig()` / `compactRoadNetworkConfig()` are
simulation-oriented policy presets that contract, clean, admit buses, and collapse parallel tracks.
Destructive heuristics are therefore opt-in via a named preset, not baked into a literal import.

## Parallel transit tracks

Per the OSM convention (`Tag:railway=tram`), a dual-track tramway is preferably mapped as two parallel
ways, one per direction, while the `oneway` tag on rails is optional and rarely applied. Rendering
both untagged tracks as two-way therefore double-counts travel direction and inflates transit link
counts.

`collapseParallelTransitTracks` (default **on**) detects a pair of transit-track ways of the same
`railway` type whose two endpoints coincide within 30 m and treats them as one physical corridor:
the lexicographically smaller way id is kept and the parallel duplicate is dropped. A lone untagged
track remains bidirectional (literal OSM semantics). Set the flag false to keep the literal reading.
This is derived from the OSM wiki and geometry only.

## Known limitations (deferred)

- **Via-way turn restrictions are preserved but NOT enforced.** A restriction whose `via` is a way
  (rather than a node) has all of its via nodes retained, so the geometry and topology are intact, but
  the routing layer does not yet enforce the multi-link ban. The count is surfaced as
  `SignalReadinessReport.unimplementedViaWayRestrictions` and reported in the summary, so callers must
  not treat turn-restriction enforcement as complete. This is a known routing limitation, not a
  faithful-import defect.
- Turn-restriction-aware cleaning (colored-subgraph expansion) is deferred; the cleaner operates on
  the plain directed graph, and a later validation drops restriction sequences whose links no longer
  exist.
- `OsmStopHintExtractor.findParentRelations` is O(n²)-ish on large inputs (pre-existing).
- Toronto/Seattle/Melbourne reference artifacts are generated on demand with pt2MATSim (black box) but
  are not checked in; current continuous measurements are Luxembourg-only.
