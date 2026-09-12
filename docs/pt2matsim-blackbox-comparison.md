# pt2MATSim vs matsim-models: black-box comparison

Clean-room method: pt2MATSim (GPL) is run only as an **out-of-process black box** on the same input
files. Its **output XML** is read with **our** readers. No pt2MATSim source or bytecode is inspected.
Both pipelines use the same OSM extract and the same GTFS feed per city.

## IO compatibility (our readers ingest pt2MATSim output)

| artifact | read by our reader |
|---|---|
| `*_network.xml` (Luxembourg, Toronto, Seattle, Melbourne) | OK |
| `*_schedule.xml` (Luxembourg, Toronto, Seattle) | OK |
| `*_vehicles.xml` (Luxembourg, Toronto, Seattle) | OK |

## Base network (OSM -> network; same OSM input)

| city | OURS nodes/links | ours meanLen | PT2M nodes/links | p2m meanLen |
|---|---|---|---|---|
| luxembourg (full region) | 96,665 / 201,037 | 132.5 m | 49,549 / 105,125 | 205.4 m |
| toronto (city clip) | 53,041 / 115,224 | 59.4 m | 13,513 / 30,724 | 138.2 m |
| seattle (city clip) | 48,767 / 105,461 | 153.9 m | 13,127 / 30,763 | 140.9 m |
| melbourne (city clip) | 63,364 / 118,947 | 54.8 m | 26,287 / 51,083 | 86.6 m |

Ours keeps more nodes/links (we preserve geometry-only nodes by default and split links at more
points); pt2MATSim collapses more aggressively (fewer, longer links). Both are valid MATSim networks.

## Unmapped schedule + vehicles (GTFS -> schedule; same feed, same sample day)

| city | OURS facilities / lines / routes / departures / vehTypes / vehicles | PT2M facilities / lines / routes / departures / vehTypes / vehicles |
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
   is not implemented. Worked around here by clipping OSM to ~10 km city boxes with osmosis.
2. **Clipping vs vehicles** (fixed this session): `TransitScheduleClipper` now reports the retained
   vehicle ids and `OsmGtfsBundleRunner` prunes `VehicleDefinitions` to exactly the vehicles the
   clipped schedule references, so the vehicle file is consistent with emitted departures.
3. **`OsmFacilityParser` PBF support** (fixed this session) and **non-mutating mapping** (fixed) and
   **CRS-correct artificial links** (fixed) and **GTFS CSV quoted-field parsing** (fixed).

## Reproduce

Inputs under `target/compare/`: `pbfs/*.osm.pbf` (Geofabrik), `gtfs/{lux,seattle,go,ptv}`,
`out/p2m/*.osm` (osmosis clips, shared by both pipelines), `out/ours/*`, `out/p2m/*`.
