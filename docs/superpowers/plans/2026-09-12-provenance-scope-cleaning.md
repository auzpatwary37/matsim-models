# Implementation Plan — Provenance, Scope, Routability Cleaning

**Spec:** `docs/superpowers/specs/2026-09-12-network-provenance-scope-cleaning-design.md`
**Branch:** `feat/topology-contraction` → PR into `main`.

## Global constraints
- Clean-room: derive behavior only from this repo's specs and independent knowledge (OSM semantics);
  no external project's source, bytecode, or configuration. External network outputs are permitted
  only as black-box measurements in `docs/external-reference-comparison.md`.
- Determinism: sorted/insertion-ordered collections; identical input ⇒ byte-identical output.
- Apache-2.0 original code; no new runtime dependencies.
- Default way-class scope unchanged (service included); default geometry mode unchanged.
- `MATERIALIZE_GEOMETRY_NODES` stays one-node-per-OSM-node.
- Full gate: `mvn -o -B clean verify`.

## Task 3 — Way-class scope (Part B)
- Add an excluded-class set to `OsmNetworkBuildConfig` (default empty = all current rules); thread through.
- Add `compactRoadNetworkConfig()` excluding `highway=service`.
- Tests: default admits service; compact preset drops a service way.
- Re-measure Luxembourg with the preset.

## Task 4 — Routability cleaner (Part C)
- Replace `OsmNetworkCleaner` with a mode-aware cleaner:
  - invalid-element removal (missing endpoints / non-finite / non-positive length) except `pt_` ids;
  - per configured routable mode, compute largest strongly connected component; a link survives if in
    the largest SCC for ≥1 routable mode it permits; drop otherwise;
  - non-routable modes are not connectivity-cleaned (keep sinks/sources);
  - quarantine removed groups; issues carry source way ids via `linkRefsByLinkId`;
  - compact sidecars (linkRefs, linkIdsByOsmWayId, geometry, hints, classification) to survivors.
- Config: `cleanupIsolatedComponents` selects the new cleaner and defaults **true**; add
  `routableModes` (default `{car, bus}`). Remove the old `<3-link non-transit` behavior.
- Wire cleaning into `OsmMatsimNetworkBuilder.build()` (engine → clean → hints/indexes).
- Tests: island removed + quarantined; one-way sink removed; isolated routable stub removed;
  largest component survives; non-routable modes keep sinks/sources; zero-length removed except
  `pt_`; sidecars only reference survivors; connected fixture is a no-op.

## Task 5 — Reconcile existing tests + measure
- Fix product-level fixtures that cleaning changes (make strongly connected or opt out with a comment).
- Update docs: spec stage-5 note, plan model, comparison doc post-cleaning counts.
- Full gate green.

## Task 6 — PR into main
- Push branch; open PR `feat/topology-contraction` → `main` with all changes; report URL.
