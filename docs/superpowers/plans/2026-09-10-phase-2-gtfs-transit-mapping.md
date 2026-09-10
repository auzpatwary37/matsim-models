# Phase 2: GTFS Combining and Transit Mapping — Implementation Plan

Date: 2026-09-10
Base: main @ `303041f` (Phase 1 complete, 397 tests)

## Task 2A: GTFS CSV Import and Feed Combining

**Goal:** Read GTFS zip/folder feeds, parse all required/optional files, produce a typed `GtfsFeedSet` with prefixed IDs.

### Deliverables

| File | Responsibility |
|------|---------------|
| `osm/../gtfs/GtfsImporter.java` | Entry point: read zip/folder → `GtfsFeedSet` |
| `osm/../gtfs/GtfsImportConfig.java` | Feed paths, feed IDs, service date selection, options |
| `osm/../gtfs/GtfsFeedSet.java` | Multi-feed container, query views |
| `osm/../gtfs/GtfsFeed.java` | Single feed: agency, stops, routes, trips, stopTimes, calendar |
| `osm/../gtfs/GtfsStop.java` | `stop_id`, name, lat, lon, location_type, parent_station |
| `osm/../gtfs/GtfsRoute.java` | `route_id`, agency, short/long name, route_type |
| `osm/../gtfs/GtfsTrip.java` | `trip_id`, route, service_id, direction, headsign, shape |
| `osm/../gtfs/GtfsStopTime.java` | trip, stop, seq, arrival/departure times, pickup/dropoff types |
| `osm/../gtfs/GtfsCalendarRow.java` | service_id, monday-sunday, date range |
| `osm/../gtfs/GtfsCalendarDatesRow.java` | service_id, date, exception_type |
| `osm/../gtfs/GtfsFrequencyRow.java` | trip_id, start, end, headway_secs, exact_times |
| `osm/../gtfs/GtfsShapePoint.java` | shape_id, seq, lat, lon |
| `osm/../gtfs/GtfsCsvReader.java` | Robust CSV parser (quotes, missing columns, BOM, CRLF) |
| `osm/../gtfs/GtfsFeedIdCodec.java` | Sanitize, derive, collision-handle feed IDs |

### Key Rules
- Object IDs: `feedId:stopId`, `feedId:routeId`, `feedId:tripId`
- Feed ID: from config or derived from filename (sanitized to `[A-Za-z0-9_-]+`)
- `location_type` 0/empty → facility; 1-4 → metadata only
- Times stored as local seconds (no TZ conversion); ≥24:00 kept as >86400s
- Missing `route_type` → warning + default `pt`
- GTFS Flex rows skipped with aggregate warning
- Neither calendar nor calendar_dates → fatal unless `assumeAlwaysActive`

### Tests
- CSV parsing: quotes, missing optional columns, BOM, CRLF, missing route_type
- Feed ID derivation: special chars, collisions → `-2` suffix
- Multi-feed: ID prefixes, no cross-feed merging
- location_type filtering: stations excluded, platform→station coord fallback
- Service materialization: calendar + calendar_dates exceptions (add/remove)
- calendar_dates-only feed, neither-file failure, assumeAlwaysActive warning
- GTFS Flex rows skipped with warnings
- Deterministic under shuffled row order

---

## Task 2B: Departure Construction, Frequency Expansion, Schedule Production

**Goal:** Convert GTFS trips/stop_times/frequencies into `TransitSchedule` + `VehicleDefinitions`.

### Deliverables

| File | Responsibility |
|------|---------------|
| `gtfs/GtfsServiceSelector.java` | Resolve active dates: explicit, dayWithMostTrips, dayWithMostServices, all |
| `gtfs/GtfsDepartureBuilder.java` | Construct departure profiles from stop_times (schedule + frequency) |
| `gtfs/GtfsTransitScheduleBuilder.java` | Produce `GtfsTransitBuildResult` (schedule + vehicles + metadata) |
| `gtfs/GtfsTransitBuildResult.java` | Result: schedule, vehicles, selected dates, warnings |
| `gtfs/GtfsModeMapper.java` | GTFS route_type → MATSim transit mode |

### Key Rules
- Departure time = first stop_time `departure_time` as seconds from midnight
- Profile offsets: `arrival_i - dep_0`, `departure_i - dep_0`
- Empty arrival/departure on non-timepoint stops → linear interpolation
- Offsets must be non-negative, non-decreasing → violations are fatal
- Frequency: half-open interval `[start, end)`, `k·headway`, overlapping rows fatal
- Departure ID: `feedId:tripId:YYYYMMDD` (schedule) / `feedId:tripId:YYYYMMDD:HHMMSS` (frequency)
- Grouping: (routeId, directionId, stopIdSeq, offsetVector) → one TransitRoute
- `direction_id` empty ≡ `0`
- Mode mapping: 0→tram, 1→subway, 2→rail, 3→bus, 4→ferry, etc.
- Vehicles: one vehicle per departure, type by mode, conservative capacities

### Tests
- Departure construction: anchored offsets, interpolation, >24:00, monotonicity fatal
- Frequency: half-open, no double-emission, overlap rejection, exact_times metadata
- Unique departure IDs under `all` mode
- Route grouping: loop stops, direction_id equiv, offset-vector identity
- Mode mapping: all 0-12, unknown → warning + pt
- Service day selection: dayWithMostTrips counts expanded departures
- Vehicle generation: every departure's vehicle resolves

---

## Task 2C: Stop-Link Candidate Scoring

**Goal:** Use Phase 1 spatial indexes to find and score candidate links for each GTFS stop.

### Deliverables

| File | Responsibility |
|------|---------------|
| `mapping/TransitMappingConfig.java` | All mapping parameters |
| `mapping/StopCandidateScorer.java` | Score links near stops using Phase 1 indexes |
| `mapping/StopCandidate.java` | Candidate record: linkId, distance, score, hint info |
| `mapping/CandidateScoreWeights.java` | Weight vector with validation (no negatives, not all-zero) |

### Scoring Factors (weighted sum)
1. Point-to-link distance (polyline)
2. Mode compatibility (including OSM access)
3. OSM stop hint proximity (stop-area outranks proximity)
4. Name/ref/operator similarity
5. Side-of-road / platform hints
6. Lane hints (bus/psv)
7. Penalty: inaccessible direction
8. Penalty: implausible turn (soft, never hard-reject)

### Config Parameters
- `nLinkThreshold`, `candidateDistanceMultiplier`
- `maxLinkCandidateDistanceMeters`
- `routingWithCandidateDistance`
- Per-factor weights (validated at config time)
- Per-mode overrides

### Rules
- No candidate found → artificial loop link at stop coordinate
- `maxLinkCandidateDistanceMeters == 0` → all stops get artificial loops

### Tests
- Scoring with stop hints: stop-area association outranks pure proximity
- Lane hint bonus for bus stops near bus-lane links
- Mode incompatibility exclusion
- Weight validation: negative/all-zero rejected
- Zero-candidate-distance → all artificial loops
- No candidate → loop link created

---

## Task 2D: Transit Route Mapping and Artificial Links

**Goal:** Map transit routes onto the network using pseudo-graph Dijkstra, create artificial links where needed.

### Deliverables

| File | Responsibility |
|------|---------------|
| `mapping/TransitNetworkMapper.java` | Entry point: map schedule → `TransitMappingResult` |
| `mapping/TransitMappingResult.java` | Result: mapped schedule, modified network, index, reports |
| `mapping/PseudoGraphRouter.java` | Build pseudo graph, run Dijkstra for candidate selection |
| `mapping/RoutePathFinder.java` | Least-cost paths between candidate pairs (bounded, cached) |
| `mapping/TurnRestrictedState.java` | Routing state tracking disallowed sequences |
| `mapping/ArtificialLinkFactory.java` | Create artificial links (connector + loop) |
| `mapping/MappingPlausibilityChecker.java` | Post-mapping validation and warnings |

### Algorithm (per transit route)
1. Select link candidates for every stop (capped)
2. Build pseudo graph (nodes = candidates)
3. Least-cost paths between adjacent-stop candidate pairs (multi-target Dijkstra)
4. Add pseudo edges (path cost + candidate penalties)
5. Penalize same candidate for adjacent stops
6. Dijkstra on pseudo graph → one candidate per stop
7. Materialize artificial links for unreachable/over-threshold transitions
8. Create child stop facilities
9. Assemble TransitRoute network route
10. Cleanup + plausibility checks

### Turn-Restricted Routing
- State: (currentNode, recent incoming link sequence suffix)
- Partial sequence allowed; completing full sequence forbidden
- Uses Phase 1 `TurnRestrictionIndex`

### Artificial Links
- Connect `toNode(upstream)` → `fromNode(downstream)`
- Loop: new node → itself
- Fields: length≥1.0m, freespeed, capacity, lanes, route mode, `pt_` prefix
- IDs: derived from (routeId, stopId, transition) tuple, collision-checked
- Excluded from later candidate selection (order independence)

### Tests
- Pseudo-graph selection where greedy nearest-link is wrong
- Turn-restricted routing: single-link and multi-link disallowed sequences
- Artificial link creation: no path, over-threshold, loop
- Field validity: positive length/capacity/freespeed/lanes
- Order independence: shuffle route processing order → same result
- Route continuity: stop links in order, single output mode, gaps = artificial links

---

## Task 2E: Child Stop Facilities, Cleanup, Reports

**Goal:** Create child stop facilities per (parent, link) combo, run cleanup, produce reports.

### Deliverables

| File | Responsibility |
|------|---------------|
| `mapping/ChildStopCreator.java` | Create child `TransitStopFacility` per parent/link |
| `mapping/MappingCleanup.java` | Post-mapping network cleanup |
| `mapping/MappingReport.java` | Structured report: candidates, diagnostics, warnings |

### Child Stop Convention
- ID: `parentStopId.link:linkId`
- Collision check: escape `.` in parent by doubling
- Metadata: `gtfs:parentStopId`, `gtfs:feedId`, `gtfs:agencyId`, `osm:stopHintId`, `osm:wayIds`
- Parent facility retained; unmapped stops keep original ID with loop link

### Tests
- Child stop creation: shared parent → multiple children
- ID collision escaping
- Parent retained, child references correct link
- Metadata keys present and round-trip through XML
- Plausibility warnings: isolated stops, zero-length routes
- XML round-trip: schedule + network + vehicles (full stack)

---

## Verification Checklist

- [ ] `mvn clean verify -B` green
- [ ] No `org.matsim` imports (only allowlisted class hint)
- [ ] `GtfsScheduleRoundTripTest`: schedule round-trips
- [ ] `MappedChildStopsTest`: child stops with parentId metadata
- [ ] `MappedRouteContinuityTest`: continuous paths, single mode
- [ ] `ArtificialLinkTest`: valid fields, order-independent
- [ ] `TurnRestrictionRoutingTest`: restrictions honored in mapping
- [ ] `VehicleDefinitionsTest`: all vehicles resolve
- [ ] `DeterministicOutputTest`: byte-identical under shuffled input
- [ ] `NoMatsimReferencesTest`: clean scan
- [ ] `PublicApiSurfaceTest`: JDK + library types only
