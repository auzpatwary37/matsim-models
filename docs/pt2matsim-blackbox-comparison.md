# pt2MATSim vs matsim-models: black-box comparison

This document compares `matsim-models` output against **pt2MATSim** (GPL), used strictly as an
external black-box oracle.

Clean-room model, stated precisely:

- The production implementation was **not** derived from any external source or bytecode; behavior
  comes from this repo's specs, OSM semantics, and public format documentation.
- pt2MATSim is **only invoked out of process** as a black box on the same input files; only its
  **output XML** is read, with **our** readers.
- The oracle harness (`tools/oracle/`) contains only independently authored **invocation** and a few
  **overrides**; pt2MATSim **generates its own default configuration at run time**. No external
  config example, source, or jar is vendored into this repository or the build.

"the reference" / "REF" below is shorthand for pt2MATSim's black-box output.

## IO compatibility (our readers ingest pt2MATSim output)

| artifact | read by our reader |
|---|---|
| `*_network.xml` (Luxembourg, Toronto, Seattle, Melbourne) | OK |
| `*_schedule.xml` (Luxembourg, Toronto, Seattle) | OK |
| `*_vehicles.xml` (Luxembourg, Toronto, Seattle) | OK |

## Base network (OSM -> network; same OSM input)

| city | OURS nodes/links | ours meanLen | pt2MATSim nodes/links | pt2M meanLen |
|---|---|---|---|---|
| luxembourg (full region) | 96,665 / 201,037 | 132.5 m | 49,549 / 105,125 | 205.4 m |
| toronto (city clip) | 53,041 / 115,224 | 59.4 m | 13,513 / 30,724 | 138.2 m |
| seattle (city clip) | 48,767 / 105,461 | 153.9 m | 13,127 / 30,763 | 140.9 m |
| melbourne (city clip) | 63,364 / 118,947 | 54.8 m | 26,287 / 51,083 | 86.6 m |

Ours keeps more nodes/links (we preserve geometry-only nodes by default and split links at more
points); pt2MATSim collapses more aggressively (fewer, longer links). Both are valid MATSim
networks.

## Post-contraction (Task 8)

The topology-contraction engine (Tasks 1–7) is now wired into `OsmSignalAwareSimplifier.simplify(...)`,
so the bundle runner's base network is the **post-contraction** network. Re-running the black-box
comparison on the **same** `lux.osm` input (network only, no GTFS) gives:

| | OURS pre (adfb47a) | OURS post (6c70ea5) | pt2MATSim (black box) |
|---|---|---|---|
| nodes | 96,665 | 75,483 (−21.9%) | 49,549 |
| links | 201,037 | 164,063 (−18.4%) | 105,125 |
| mean link length | 132.5 m | 161.7 m | 205.4 m |

Contraction moved our network materially toward pt2MATSim: the same physical road is no longer
re-split at every way boundary, so mean link length rose 22% while link count fell 18%.

### Per-road-class link counts

Parsed from the `osm:tag:highway` attribute in our network and `osm:way:highway` in the pt2MATSim
network (streaming, link-scoped). `(rail/other)` = links with no `highway` class attribute (rail).
The last column is pt2MATSim run with `highway=service` kept, for a like-for-like scope.

| class | OURS pre | OURS post | pt2M default | pt2M service-kept |
|---|---|---|---|---|
| motorway | 903 | 482 | 1,321 | 1,332 |
| motorway_link | 871 | 680 | 777 | 816 |
| trunk | 214 | 130 | 232 | 233 |
| trunk_link | 105 | 80 | 92 | 94 |
| primary | 19,691 | 15,082 | 14,355 | 17,094 |
| primary_link | 322 | 267 | 220 | 232 |
| secondary | 30,148 | 21,897 | 24,225 | 28,348 |
| secondary_link | 131 | 119 | 92 | 95 |
| tertiary | 6,098 | 4,449 | 4,727 | 5,492 |
| tertiary_link | 27 | 25 | 24 | 24 |
| unclassified | 9,133 | 7,058 | 8,275 | 10,358 |
| residential | 54,908 | 44,547 | 37,154 | 44,113 |
| living_street | 2,838 | 2,321 | 2,202 | 2,433 |
| service | 63,806 | 57,074 | 1,151 | 56,527 |
| busway | 20 | 19 | 7 | 7 |
| construction | 0 | 0 | 43 | 43 |
| path | 0 | 0 | 20 | 22 |
| pedestrian | 0 | 0 | 11 | 11 |
| platform | 0 | 0 | 2 | 2 |
| track | 0 | 0 | 2 | 2 |
| (rail/other) | 11,822 | 9,833 | 10,193 | 10,690 |
| **TOTAL** | **201,037** | **164,063** | **105,125** | **177,968** |

### Remaining delta expressed as policy choices

1. **`highway=service` scope (dominant term).** Our default keeps service ways: 57,074 post-contraction
   links. The reference's default drops almost all of them (1,151); when told to keep service it emits
   56,527 and 177,968 links / 82,167 nodes, bracketing our total. This is a deliberate default-scope
   choice — **not** a defect, and explicitly out of scope for Task 8 (the plan forbids changing
   `service`/`busway` scope while re-measuring).
2. **Shared non-service road scope is now nearly aligned.** Excluding `service` and `(rail/other)`,
   ours = 97,156 links at 191.4 m mean; reference default = 93,781 links at 199.1 m mean — a **3.6%
   link-count difference**. Contracted routing-graph compactness is comparable on the shared scope;
   the headline totals differ mainly because of (1).
3. **Motorway node retention.** Ours has fewer, much longer motorway links (482 @ 975.0 m) than
   pt2MATSim (1,321 @ 356.7 m). Our contract rule dissolves every degree-2 node with no intrinsic
   reason; motorway carriageway/continuation nodes carry none. The reference retains more motorway
   nodes (e.g. structure/interchange points). Policy choice, not a defect.
4. **Accepted way classes.** The reference additionally materializes `path`, `pedestrian`, `platform`,
   `construction`, and `track` (78 links total in the default column) that we do not admit to the car
   network; our missing class is rail (9,833 vs 10,193). Policy choice on which way classes feed the
   network. The `(rail/other)` bucket also contains a couple of ferry links (from `route=ferry`),
   which is why it is labelled rail/other rather than rail alone.
5. **Mean length.** Ours 161.7 m vs pt2MATSim 205.4 m is partly the short service aisles in our
   network and partly the extra rail; on the shared non-service scope the means differ by <4%
   (191.4 m vs 199.1 m).

Net: contraction closed most of the gap; what remains is explainable by `service` scope, motorway
node-retention policy, and accepted-class policy — all choices, not correctness defects.

### Reproduce (post-contraction)

```bash
# build current branch classes + deps (do NOT use the stale installed jar)
cd <repo>   # branch feat/topology-contraction
mvn -o -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp_topo.txt
java -Xmx56g -cp "target/classes:$(cat /tmp/cp_topo.txt)" \
  com.citymodeler.matsim.models.bundle.OsmGtfsBundleRunner \
  <workdir>/lux.osm none \
  <workdir>/ours/luxembourg-post
# ours -> <workdir>/ours/luxembourg-post/network.xml
# ref  -> <workdir>/ref_lux_full_network.xml         (default)
#         <workdir>/ref_lux_full_service_network.xml (service kept)
```

## Post-cleaning (routability + scope pass)

The topology pipeline now (a) cleans each configured routable mode to its largest strongly connected
component (default modes `car`, `bus`), quarantining rather than silently deleting disconnected
fragments, and (b) persists full link provenance (geometry, source OSM ways/nodes, names). Both are
described in `docs/superpowers/specs/2026-09-12-network-provenance-scope-cleaning-design.md`.

Luxembourg, default scope, `lux.osm`:

| | post-contraction | post-cleaning |
|---|---|---|
| nodes | 75,483 | 73,347 |
| links | 164,063 | 161,016 |

Cleaning removed 1,136 nodes / 3,047 links of disconnected fragments and one-way sinks. The car
routable subgraph is now **one strongly connected component covering 100.00% of its nodes** (verified
by an independent Kosaraju audit of the emitted `network.xml`); before this pass routability was not
guaranteed. Removed components are reported as quarantine issues (never silently dropped).

### Scope parity preset

`OsmNetworkBuildConfig.compactRoadNetworkConfig()` matches pt2MATSim's default
OSM-converter scope and contraction policy: it excludes `highway=service` and caps contracted link
length at **500 m** (`maxContractedLinkLengthMeters`), so a degree-2 node is retained where dissolving
it would produce a longer link. It also admits buses on car roads (`addBusToCarRoads`).

| network | ours nodes/links | pt2MATSim nodes/links |
|---|---|---|
| default scope, post-cleaning (service kept) | 73,347 / 161,016 | — (177,968 links when pt2MATSim keeps service) |
| parity preset (service excluded, 500 m car cap, bus on roads) | **50,113 / 106,084** | **49,549 / 105,125** |

The parity preset matches pt2MATSim to **+1.1% nodes / +0.9% links**. The car routable
subgraph is effectively **one strongly connected component (100.0% of its nodes)** in both networks
(ours reports a handful of singleton SCCs from a few self-contained fragments). Direction defaults
match empirically: rail **2.01** directed/physical (bidirectional) and tram **2.00** before
parallel-track collapse; after collapsing parallel tram tracks (one corridor per physical pair) our
tram is 1,228 directed links over 614 spans.

**Toronto (repo fixture `src/test/resources/osm/cities/toronto.osm.gz`, 4,009 ways):** a real
reference **was** generated by running pt2MATSim as a black box on the same `toronto.osm`
(same default scope/params as the Luxembourg run):

| | ours | pt2MATSim (black box) |
|---|---|---|
| nodes | 1,176 | 759 |
| links | 2,465 | 1,407 |
| mean link length | 61.6 m | 105.5 m |

The reference (pt2MATSim output) is produced on demand; it is not checked in. The remaining gap is the same policy set as
Luxembourg (we keep more geometry-only detail and admit more railway), plus a genuine **tram direction
divergence**: pt2MATSim emits `railway=tram` **oneway** (386 directed / 386 physical = 1.00) while
we emit it **bidirectional** (780 directed / 390 physical = 2.00; both endpoints untagged in OSM).
`railway=rail` agrees at 2.00 in both. Our tram policy follows OSM semantics (untagged = two-way); the
pt2MATSim applies a oneway default to tram. This is a policy choice to reconcile, not a defect.

Remaining per-class delta is policy, not connectivity: `service` −1,151 (pt2MATSim keeps
transit-carrying service via a keep-with-public-transit option, which we exclude entirely),
`(rail/other)` −1,162 (pt2MATSim keeps `platform`/`narrow_gauge`/`abandoned` links we drop), and a
few hundred cap-boundary links in `residential`/`secondary`/`tertiary`.

**How the gap was diagnosed.** A spatial difference map (links rasterized to 50 m cells, proximity
join) showed the earlier deficit was *not* a missing region — it was scattered short fragments — and an
independent SCC audit showed both cleaned car graphs were already 100% strongly connected (so our
connectivity was not broken). The cause was contraction policy: pt2MATSim stops thinning at 500 m
(its longest link 3,628 m), while we had no cap (our longest 10,007 m). On the 37,770 shared OSM ways
our link density already matched pt2MATSim (ratio 0.99); the deficit was long merged links, not
missing roads. Adding the same 500 m cap reproduces pt2MATSim's counts.

### `laneDefinitions` — a new artifact with no black-box counterpart

The `laneDefinitions.xml` produced by this branch is a **new artifact**. pt2MATSim is a
network/schedule converter and emits no lane file, so there is **nothing to diff side-by-side**; the
only comparable quantity remains the base network (the 1,176 / 2,465 vs 759 / 1,407 row above). What
follows is our own output plus sanity checks, not a black-box comparison. Both runs below were made
with the bundle runner on the unmodified `toronto.osm` fixture (same OSM both scopes); the lane file
is emitted unconditionally by `OsmGtfsBundleRunner` (Part 3).

| | default runner (`materializeGeometryConfigWithCleanup`) | parity preset (`compactRoadNetworkConfig`) | pt2MATSim (black box) |
|---|---|---|---|
| base network nodes | 1,661 | 1,176 | 759 |
| base network links | 3,569 | 2,465 | 1,407 |
| mean link length | 55.7 m | 61.4 m | 105.5 m |
| `lanesToLinkAssignment` | 3,569 | 2,465 | n/a (no lane output) |
| links covered by a lane | 3,569 | 2,465 | n/a |
| `<lane>` elements | 4,240 | 3,031 | n/a |
| mean lanes / assignment | 1.188 | 1.230 | n/a |

**Lane-set shape** (per assignment / per lane):

| metric | default runner | parity preset |
|---|---|---|
| lanes per assignment `{n: assignments}` | `{1: 3,077, 2: 333, 3: 139, 4: 20}` | `{1: 2,053, 2: 277, 3: 116, 4: 19}` |
| `leadsTo/toLink` per lane `{n: lanes}` | `{1: 658, 2: 1,809, 3: 1,219, 4: 551, 5: 3}` | `{1: 486, 2: 1,609, 3: 545, 4: 391}` |
| `osm:lane.count` per lane | `{0: 3,569, 1: 492, 2: 159, 3: 20}` | `{0: 2,465, 1: 412, 2: 135, 3: 19}` |
| `osm:lane.capacity.shared` per lane | `{true: 3,582, false: 658}` | `{true: 2,545, false: 486}` |
| `osm:lane.alignmentProvenance` per lane | `{default: 4,240}` | `{default: 3,031}` |

**Distribution of `osm:lane.confidence`** (per lane; the vocabulary is `LaneConfidence`):

| confidence value | default runner | parity preset | meaning |
|---|---|---|---|
| `absent` | 4,047 (95.5%) | 2,860 (94.4%) | no OSM lane evidence → one unrestricted fallback lane |
| `present` | 182 (4.3%) | 162 (5.3%) | derived from an explicit `turn:lanes` / `lanes` tag |
| `wiki-default-even-split` | 11 (0.3%) | 9 (0.3%) | even `lanes` total split per-direction (OSM wiki rule) |

The fixture has 96 ways carrying `turn:lanes`, which is the source of the small `present` tail; the
overwhelming majority of links have no lane tagging at all and therefore get the spec-mandated
single unrestricted lane.

**Sanity checks (all pass, both scopes).** `LanesXmlReader(true)` validates the emitted file against
the vendored `laneDefinitions_v2.0` XSD; every `lanesToLinkAssignment.linkIdRef` resolves to a link in
the same base network (0 dangling refs); every emitted non-dead-end link has exactly one assignment
(this fixture has 0 dead-end links, so assignments = links); and the writer never emits a lane with an
empty `leadsTo` (each has ≥1 `toLink`), as the XSD requires.

**Open item (scope, not a lane defect).** The existing Toronto row above records ours at
1,176 / 2,465, which is the **scope-parity preset**; the default bundle runner keeps
`highway=service` and therefore emits a larger 1,661 / 3,569 network and 3,569 lane assignments on
the same fixture. This is the same `service`-scope policy already documented for Luxembourg, not a
regression — the lane file simply inherits whatever base network the chosen preset produces. The
Toronto lane run was network-only (no GTFS): the compare workspace holds only the Luxembourg GTFS
feed, so no mapped-schedule black-box comparison was possible here. Route continuity / turn
restriction / artificial-link / child-stop correctness for the mapping stage is instead covered by
our own mapping tests (`MappedRouteContinuityTest`, `TurnRestrictionRoutingTest`, `ArtificialLinkTest`,
`MappedChildStopsTest`, `DeterministicOutputTest`).

### Mode model

pt2MATSim tags its roads `bus,car` (55,632 links) or `bus,car,pt` (37,391); our base network tagged
roads `car` only, so a raw mode-count comparison showed far fewer "transit" links for us. That was a
labelling difference, not missing links: our pipeline materializes the transit mode during mapping
(one output mode per route, `car` as fallback eligibility; spec §Mode assignment). The parity preset
now adds `bus` to every car road (`addBusToCarRoads`) so the bus routable subnetwork exists in the base
network too, matching pt2MATSim.

The 500 m length cap applies to **car roads only**, never to rail/tram (pt2MATSim does not
length-cap rail; applying the car cap to rail over-segments it). Direction defaults are matched
empirically from pt2MATSim's output: **rail is bidirectional** (9,356 directed / 4,667 physical
spans = 2.00) and **tram is oneway** (640 / 640 = 1.00) in that converter. Turn restrictions cover
every mode the originating link permits (so `car,bus` roads restrict both) minus `except=`
exceptions.

### Road-name provenance for stop mapping

Links carry `osm:name` / `osm:sourceNames` (54.4% of Luxembourg links named). `StopCandidateScorer`
now uses them for the spec's "same name similarity" candidate criterion
(`CandidateScoreWeights.nameSimilarity`), disambiguating among nearby candidate links using the GTFS
stop name.

### Provenance now emitted per link

Every contracted link carries `osm:geometry` (WKT LINESTRING), `osm:sourceWays`, `osm:sourceNodes`,
`osm:name`, and `osm:sourceNames` (only when a merged link spans more than one distinct name). These
round-trip through `NetworkXmlWriter`/`StreamingNetworkWriter` and `NetworkXmlReader`, so a merged
link's full source polyline and OSM way/node lineage survive to disk. This fixes the earlier defect
where a 269-segment, ~9.7 km merged link was attributed to a single 172 m source way.

## Unmapped schedule + vehicles (GTFS -> schedule; same feed, same sample day)

| city | OURS facilities / lines / routes / departures / vehTypes / vehicles | pt2M facilities / lines / routes / departures / vehTypes / vehicles |
|---|---|---|
| luxembourg | 2,706 / 717 / 3,120 / 15,889 / 3 / 16,099 | 2,786 / 722 / 2,308 / 16,099 / 8 / 16,099 |
| toronto (GO) | 30 / 13 / 506 / 729 / 2 / 1,872 | 885 / 45 / 968 / 1,872 / 2 / 1,872 |
| seattle (Sound) | 2,910 / 93 / 6,749 / 9,137 / 3 / 13,036 | 6,276 / 145 / 4,588 / 13,036 / 4 / 13,036 |
| melbourne (PTV) | 15 / 6 / 27 / 15 / 1 / 413 | (feed rejected upstream: unknown agency id) |

Observations:
- **Vehicles**: both produce exactly **one vehicle per departure**; totals match pt2MATSim exactly
  for the sample day (16,099 / 1,872 / 13,036). This independently confirms the spec's
  "one vehicle per departure" model. Ours reports fewer *used* departures after clipping out-of-extent
  stops (e.g. Luxembourg 15,889 vs 16,099) while the vehicle file still lists all built departures —
  a known consistency gap to address (see below).
- **Routes**: ours groups trips into more MATSim routes (3,120 vs 2,308 in Luxembourg; 6,749 vs 4,588
  in Seattle) because we group by the **exact per-second offset vector** (spec §Transit Schedule
  Production), while a coarser grouping yields fewer routes. Both are deterministic.
- **Facilities/lines**: same order of magnitude; differences reflect station/platform handling and
  clipping.

## Issues surfaced in OUR pipeline while running at real scale

1. **In-memory import**: `OsmNetworkImporter` / `GtfsImporter` load the entire input into memory.
   A 754 MB OSM XML or a large multi-agency GTFS OOMs at 24 GB. The spec's streaming memory strategy
   is not implemented. Worked around here by clipping OSM to ~10 km city boxes.
2. **Clipping vs vehicles** (fixed this session): `TransitScheduleClipper` now reports the retained
   vehicle ids and `OsmGtfsBundleRunner` prunes `VehicleDefinitions` to exactly the vehicles the
   clipped schedule references, so the vehicle file is consistent with emitted departures.
3. **`OsmFacilityParser` PBF support** (fixed this session) and **non-mutating mapping** (fixed) and
   **CRS-correct artificial links** (fixed) and **GTFS CSV quoted-field parsing** (fixed).

## Full-gate note (lanes + mapping hardening)

`mvn -o -B clean verify` on `feat/lanes-and-mapping` (network-only bundle artifacts added in
`laneDefinitions.xml`; no production code changed by this docs task):

```
Tests run: 660, Failures: 0, Errors: 0, Skipped: 0
You have 0 Checkstyle violations.
SpotBugs: 0 findings at threshold High.
BUILD SUCCESS
```

- **Tests: 660 / 0 failures / 0 errors / 0 skipped** (up from the pre-branch suite; includes the new
  lane, mapping, GTFS, and GeoJSON tests).
- **Checkstyle: 0 violations.** 99 *warnings* are still reported (`failsOnViolation=false`, so the
  build is green); they are pre-existing style nits concentrated in `GtfsFeed.java` (28),
  `XmlSupport.java` (22), and `GtfsImporter.java` (9), none in the lane/mapping code added here.
- **SpotBugs: 0 High.** The plugin runs at `threshold=High` with `failOnViolation=false`; no High
  findings were emitted.
- Note: the Checkstyle and SpotBugs plugin parameter names log a Maven warning (`failsOnViolation` /
  `failOnViolation` unknown for these plugin versions) — benign configuration noise, not a finding.

## Reproduce

Inputs under `target/compare/`: `pbfs/*.osm.pbf` (Geofabrik), `gtfs/{lux,seattle,go,ptv}`,
`out/ref/*.osm` (clips, shared by both pipelines), `out/ours/*`, `out/ref/*`. The lane/mapping runs
above use the in-repo Toronto fixture and need no GTFS feed:

```bash
# network-only bundle (base network + laneDefinitions.xml)
# NB: the network importer transparently handles .gz, but the facility parser (stage 1) does not,
# so decompress the fixture first for the end-to-end bundle run.
gunzip -c src/test/resources/osm/cities/toronto.osm.gz > /tmp/toronto.osm
mvn -o -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp_lanes.txt
java -cp "target/classes:$(cat /tmp/cp_lanes.txt)" \
  com.citymodeler.matsim.models.bundle.OsmGtfsBundleRunner \
  /tmp/toronto.osm none /tmp/toronto-lanes
# -> /tmp/toronto-lanes/{network.xml,laneDefinitions.xml,facilities.xml}
# parity-preset scope (1,176 / 2,465) via OsmNetworkBuildConfig.compactRoadNetworkConfig().
```
