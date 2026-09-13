# Network Provenance, Scope, and Routability Cleaning — Design

**Status:** approved in brainstorming (2026-09-12). Supersedes the cleaning/scope gaps noted by the
topology-contraction plan's Task 8.

## Problem

The topology-contraction engine now produces a compact, cross-way network, but three gaps remain:

1. **Provenance is lost and misleading.** A merged link records only the *first* source way id and
   that way's tags/name. The geometry sidecar (full polyline) exists in memory but is never written,
   so it does not survive a `network.xml` round-trip. There is no record of all source way ids or the
   ordered source OSM node ids. Example: a 9,729 m, 269-segment merged link is attributed to way
   `1441606921` ("An der Waark") whose physical extent is 172 m over 4 nodes.
2. **Way-class scope differs from pt2MATSim by ~2×, and the difference is policy, not topology.**
   Luxembourg post-contraction: 164,063 links vs pt2MATSim 105,125. `highway=service` alone accounts
   for +55,923 links / +4,993 km (95% of the gap). Excluding service the totals are 106,989 vs 103,974
   (+2.9%) and total road length matches within 0.2%. The default scope is currently hard-coded.
3. **Cleaning does not guarantee routability.** The spec's Network Cleaning stage (stage 5) is not
   implemented: the existing `OsmNetworkCleaner` removes only components smaller than 3 directed links
   with no transit mode, is not per-mode, and is off by default. Default output can contain islands,
   one-way sinks, and dead-ends. A routed simulation requires each configured routable mode to be
   strongly connected.

## Decisions (binding)

- **Component rule:** per configured routable mode, keep only the largest **strongly connected**
  component (every remaining link is reachable from and can return to every other link for that mode).
  Non-routable modes retain sinks/sources. This is a deliberate correction of the older spec's
  "weakly connected" wording, which does not guarantee routing.
- **Cleaning runs by default** as a normative stage of both `build()` and signal-ready `simplify()`.
- **Quarantine, never silent deletion:** removed components are retained in
  `OsmNetworkBuildResult.quarantinedComponents()` with issues carrying source OSM ids. Transit-tagged
  components are quarantined, never silently dropped.
- **Scope is configurable**, default unchanged (service included, per the original spec §462); a
  parity preset excludes service to compare with pt2MATSim.
- **Geometry encoding:** WKT `LINESTRING (x y, x y, ...)` in the network's projected coordinates.
- **Names:** `osm:name` = representative name; `osm:sourceNames` emitted only when the chain spans
  more than one distinct name.

## Part A — Provenance round-trip

The engine already computes the full polyline and the ordered source segments. Persist per emitted
link, as link attributes (the XSD is `xs:anyType`; no `Network`/`Link` shape change):

| attribute | value |
|---|---|
| `osm:geometry` | WKT `LINESTRING (x y, x y, ...)`, full polyline, projected coords |
| `osm:sourceWays` | all source OSM way ids, deduped, chain order, comma-separated |
| `osm:sourceNodes` | all source OSM node ids, polyline order, comma-separated |
| `osm:name` | representative name (first source way that has `name`) |
| `osm:sourceNames` | distinct names, chain order, comma-separated — only when >1 distinct |

`osm:wayId` (first source way) is retained for back-compat. Links stay merged; `linkId`, endpoints,
and topology are unchanged.

**Reader:** `NetworkXmlReader` already round-trips arbitrary attributes generically, so no reader
change is strictly required. Add a small typed helper to decode `osm:geometry` into points and expose
source lists, plus an explicit writer→reader round-trip test. Patch the reader only if the round-trip
test reveals a gap.

**Writer:** set the attributes at emit time in `OsmTopologyBuilder.emitLink`; both
`StreamingNetworkWriter` and `NetworkXmlWriter` already emit all attributes. Reuse the existing
`writeAttributes`/`readAttributes` path.

## Part B — Way-class scope

- Add a configurable admitted-class scope to `OsmNetworkBuildConfig` (set of `highway=*` values or an
  equivalent predicate), threaded through the constructors/factories. Default unchanged.
- Add `pt2matsimComparableConfig()` (or equivalently named) that excludes `highway=service`, matching
  pt2MATSim's default. Note pt2MATSim still retains ~1,151 transit-carrying service links
  (`keepWaysWithPublicTransit=true`); exact parity there requires route-relation detection and is
  explicitly **out of scope** for this pass.
- Re-measure Luxembourg with the preset and record the closed delta. Do not change the default.

## Part C — Routability cleaning

Replace the weak `OsmNetworkCleaner` with a mode-aware cleaner:

1. **Invalid-element removal:** drop links with missing endpoints, non-finite metrics, or non-positive
   length, except artificial connectors whose id starts with `pt_` (later-phase exemption). Emit issues.
2. **Per-mode strongly connected component reduction:** for each configured routable mode, compute
   strongly connected components over the directed subgraph of links permitting that mode; keep the
   largest SCC by link count. A link is dropped only if it is outside the largest SCC for **every**
   configured routable mode (so a link that is routable-car-connected survives even if it is not
   bus-connected). Non-routable modes (those not in the configured routable set) are not reduced.
3. **Zero-length removal** (with the `pt_` exemption) as in step 1.
4. **Quarantine:** removed link groups retained on the result; issues carry source OSM way ids where
   resolvable via `linkRefsByLinkId`.
5. **Sidecar compaction:** linkRefs, linkIdsByOsmWayId, geometry, lane/stop hints and classification
   are compacted to surviving ids. Deterministic.

**Turn-restriction awareness:** restrictions can make a link look disconnected when a forbidden turn
is the only path. For this pass, cleaning operates on the *plain* directed subgraph, then a validation
stage drops any stored `disallowedNextLinks` sequence whose links no longer all exist / remain
contiguous (already a spec requirement). Turn-restriction-*aware* cleaning (colored-subgraph
expansion, as pt2MATSim does) is explicitly **deferred**; record this as a known limitation.

**Pipeline order** (spec §Network Cleaning), implemented in `OsmTopologyBuilder.buildCore`:
select/contract → normalize metrics → component cleaning + quarantine + sidecar compaction →
(build) → restriction translation/validation against final ids (signal-ready path).

**Config:** `cleanupIsolatedComponents` becomes the toggle for the *new* cleaner and defaults **true**
for `build()`/`simplify()`. The old `<3-link non-transit` behavior is removed.

## Testing

- **Provenance:** multi-way merged link has all source ways/nodes/names; geometry WKT decodes to the
  same vertices and length; single-way link has one way/name and no `osm:sourceNames`; writer→reader
  preserves merged ids, geometry, source ways/nodes, names.
- **Scope:** default admits service; preset excludes it; service way with no transit dropped under preset.
- **Cleaning:** a disconnected island is removed and quarantined; a one-way sink is removed under the
  routable mode; a transit-tagged island is quarantined (not dropped) and reported with source ids; the
  largest component survives; non-routable modes keep sinks/sources; zero-length removed except `pt_`;
  sidecars reference only surviving ids.
- **Invariance:** on a connected fixture, cleaning is a no-op; `oneWayAndSplitWayProduceIdenticalTopology`
  still passes.
- **Full gate:** `mvn -o -B clean verify` green; re-measure Luxembourg and record counts.

## Compatibility / risk

- Existing small fixtures that are single-island or single-link will now be cleaned. Tests must either
  build a strongly-connected fixture or opt out via config with a documented reason.
- Cleaning on by default is a deliberate output change; the default scope (service included) is NOT changed.
- Clean-room: no pt2MATSim/MATSim source or bytecode; only published docs/config/black-box output.

## Implementation notes (as built)

- **Layer:** cleaning runs inside `OsmTopologyBuilder.buildCore`, gated by
  `OsmNetworkBuildConfig.cleanupIsolatedComponents()` (default true for `defaultConfig()` /
  `materializeGeometryConfigWithCleanup()`; false for the visualization-only `materializeGeometryConfig()`).
  The `signal-ready` engine method is NOT forced to clean, so engine-level junction fixtures stay isolated
  and testable; the product bundle runner uses `materializeGeometryConfigWithCleanup()`.
- **SCC:** iterative Kosaraju over the induced node graph, one pass per `routableModes` entry
  (default `{car, bus}`), link counts accumulated in a single pass (not O(components × links)).
- **Exemption:** links whose id begins with `pt_` are exempt from zero-length and component removal.
- **Measured (Luxembourg, `lux.osm`):** before cleaning 75,483 nodes / 164,063 links; after cleaning
  73,347 nodes / 161,016 links. Car routable subgraph is one strongly connected component covering
  100.00% of its nodes (was not guaranteed before). Removed components are quarantined with issues.

## Known limitations (deferred)

- Turn-restriction-aware cleaning (colored-subgraph expansion as pt2MATSim does) is deferred; the
  cleaner operates on the plain directed graph, and a later validation drops restriction sequences
  whose links no longer exist.
- `OsmStopHintExtractor.findParentRelations` is O(n²)-ish on large inputs (pre-existing); the
  Luxembourg run takes ~15 min and is dominated by it, not by cleaning.
- pt2MATSim additionally retains ~1,151 transit-carrying `service` links (`keepWaysWithPublicTransit`);
  our parity preset excludes all service ways.

## Maximum contracted-link length (scope-parity follow-up)

Diagnosis of the earlier parity undershoot (ours 86,091 vs pt2MATSim 105,125 links) found it was not a
missing region and not a connectivity defect (both cleaned car subgraphs were 100% one SCC); it was
that pt2MATSim caps contracted link length at **500 m** (`maxLinkLength`) and retains a degree-2 node
where dissolving it would exceed the cap, whereas our engine had no cap and emitted very long links
(ours max 10,007 m vs pt2M 3,628 m). On the shared ways we already matched pt2MATSim's link density
(ratio 0.99).

`OsmNetworkBuildConfig.maxContractedLinkLengthMeters()` adds that cap (default 0 = no cap;
`pt2matsimComparableConfig()` sets 500 m). Enforcement walks each chain and promotes the node where
the accumulated length first exceeds the cap, so an emitted link may overshoot by at most one atomic
segment — matching pt2MATSim's semantics.

## Road-name scoring for stop mapping

The Phase-1 build now emits `osm:name` / `osm:sourceNames` on links (54.4% of Luxembourg links named).
`StopCandidateScorer` uses them for the spec-mandated "same name similarity" candidate criterion via
`CandidateScoreWeights.nameSimilarity`: normalized exact match scores 1.0, containment 0.75, otherwise
a token overlap. This disambiguates among nearby candidate links (e.g. both sides of a dual
carriageway) using the GTFS stop name.

