# Implementation Plan — Provenance, Scope, Routability Cleaning

**Spec:** `docs/superpowers/specs/2026-09-12-network-provenance-scope-cleaning-design.md`
**Branch:** `feat/topology-contraction` → PR into `main`.

## Global constraints
- Clean-room: no pt2MATSim/MATSim source/bytecode; no `org.matsim.*` strings except the allowlisted `DisallowedNextLinks` hints.
- Determinism: sorted/insertion-ordered collections; identical input ⇒ byte-identical output.
- Apache-2.0 original code; no new runtime dependencies.
- Default way-class scope unchanged (service included); default geometry mode unchanged.
- `MATERIALIZE_GEOMETRY_NODES` stays one-node-per-OSM-node.
- Full gate: `mvn -o -B clean verify`.

## Task 1 — Provenance attributes at emit (Part A)
- Modify `OsmTopologyBuilder.emitLink`: set `osm:geometry` (WKT LINESTRING), `osm:sourceWays`,
  `osm:sourceNodes`, `osm:name`, and `osm:sourceNames` (>1 distinct only). Thread node ids alongside
  the geometry point list.
- Modify `OsmCollapsedLink` if needed to expose ordered source node ids (derive from sourceSegments +
  geometry), else compute locally.
- Tests (`OsmTopologyBuilderTest`): multi-way link has all ways/nodes/names; single-way has one and no `osm:sourceNames`; WKT decodes to same vertices/length.

## Task 2 — Reader round-trip + helper (Part A)
- Add `OsmLinkProvenance` decode helper (or static methods on a small class) for `osm:geometry` etc.
- `NetworkXmlReader` already reads arbitrary attributes; add an explicit round-trip test
  (`NetworkXmlRoundTripTest` or extend existing writer test) asserting link count, merged ids,
  geometry, source ways/nodes, names survive writer→reader.
- Patch reader only if the test fails.

## Task 3 — Way-class scope (Part B)
- Add admitted-class scope to `OsmNetworkBuildConfig` (default = all current rules); thread through.
- Add `pt2matsimComparableConfig()` excluding `highway=service`.
- Tests: default admits service; preset drops a service way with no transit.
- Re-measure Luxembourg with the preset; append to `docs/pt2matsim-blackbox-comparison.md`.

## Task 4 — Routability cleaner (Part C)
- Replace `OsmNetworkCleaner` with mode-aware cleaner:
  - invalid-element removal (missing endpoints / non-finite / non-positive length) except `pt_` ids;
  - per configured routable mode, compute largest strongly connected component; a link survives if in
    the largest SCC for ≥1 routable mode it permits; drop otherwise;
  - quarantine removed groups; issues carry source way ids via `linkRefsByLinkId`;
  - compact sidecars (linkRefs, linkIdsByOsmWayId, geometry, hints, classification) to survivors.
- Config: `cleanupIsolatedComponents` now selects the new cleaner and defaults **true**; add
  `routableModes` (default `{car, bus}`). Remove the old `<3-link non-transit` behavior.
- Wire cleaning into `OsmMatsimNetworkBuilder.build()` (engine → clean → hints/indexes) and into
  `simplify()` after `buildSignalReady` and before restriction reading. Engine-level
  `OsmTopologyBuilder.build` stays raw (no cleaning), so engine tests are unaffected.
- Tests (`OsmRoutabilityCleanerTest`): island removed + quarantined; one-way sink removed; transit
  island quarantined not dropped with source ids; largest component survives; zero-length removed
  except `pt_`; sidecars only reference survivors; connected fixture is a no-op.

## Task 5 — Reconcile existing tests + measure
- Fix product-level fixtures that cleaning changes (make strongly connected or opt out with a comment).
- Update docs: spec stage-5 note, plan model, comparison doc post-cleaning counts.
- Full gate green.

## Task 6 — PR into main
- Push branch; open PR `feat/topology-contraction` → `main` with all changes; report URL.
