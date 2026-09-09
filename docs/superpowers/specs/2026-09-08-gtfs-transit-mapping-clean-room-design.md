# Clean-Room OSM Network And GTFS Transit Mapping Design

Date: 2026-09-08

## Purpose

CityModeler is a commercial GUI for preparing, inspecting, editing, and running MATSim scenarios. The transit pipeline must start with a high-quality OSM-derived network, because that network becomes the source of truth for later GTFS mapping, stop-to-link assignment, turn restrictions, lane hints, and route plausibility checks.

This design therefore has two phases:

- Phase 1: import OSM into a queryable, metadata-rich intermediate network, then emit a cleaned MATSim-compatible network.
- Phase 2: combine GTFS feeds, map transit routes onto the cleaned network, then emit transit schedule and vehicle files.

`matsim-models` remains an independent file/model toolkit. It must not run MATSim, depend on MATSim runtime classes, depend on Pt2MATSim, or copy GPL implementation details.

## Legal Boundary

This project follows an independent-implementation and no-copy policy. It does not claim that these controls constitute a legally validated clean-room process; any stronger "clean-room" representation requires counsel approval.

Implementation contributors must not copy or adapt protected source, comments, tests, fixtures, or other expression from MATSim or Pt2MATSim.

Allowed implementation materials, for consulting facts, standards, public interfaces, and functional requirements only. Consulting a source does not authorize copying protected expression; record each source and its license, paraphrase independently, and separately review any text, diagrams, examples, tests, or fixtures proposed for incorporation:

- OSM official wiki pages and tag documentation (OSM wiki text is CC BY-SA 2.0, which is separate from the ODbL data license)
- OSM ODbL license text and OpenStreetMap Foundation legal/attribution guidance
- GTFS official specification (the spec's own license is separate from each feed's data terms)
- MATSim XML/file-format behavior already implemented in `matsim-models`
- MATSim public documentation and examples about network, transit schedule, vehicles, and `disallowedNextLinks`
- Pt2MATSim wiki pages and published papers/theses describing public algorithms and configuration concepts
- Synthetic fixtures created specifically for this repository
- User-provided sample feeds/networks, if license-compatible

Disallowed implementation sources:

- Copying or adapting Pt2MATSim Java source code
- Copying or adapting MATSim GPL source code
- Copying GPL comments, names of internal helper classes, method bodies, tests, or fixture files
- Bundling Pt2MATSim or MATSim jars in this library

### Software License Versus Data License

These are separate matters and must not be conflated:

- The independently authored `matsim-models` code is Apache-2.0. A software license does not grant rights in imported data.
- ODbL governs OSM data and OSM-derived databases, not the importer program. Producing MATSim-format output does not make the output "GPL" or the code "ODbL".
- Each GTFS feed is governed by that feed's own publisher terms, which may be absent. The GTFS specification's license says nothing about a particular feed's data rights.
- A combined OSM+GTFS output bundle contains components with different terms: OSM-derived network data, GTFS-derived schedule data, generated vehicles, and software-generated metadata. Preserve them as separately identifiable components with separate license notices. Before any public release, review whether cross-references and added properties keep the components a Collective Database or turn them into a Derivative Database. Do not label the entire bundle ODbL, Apache-2.0, or under a feed license without that review.

### OSM / ODbL Position

OSM data is licensed under ODbL. Commercial use is allowed. The obligations below are engineering guidance, not legal advice; release documentation must tell commercial users to review ODbL obligations for their deployment model.

Key ODbL concepts, applied conservatively:

- Public Use: distributing OSM data, a Derivative Database, or a Produced Work built from either outside your organisation. Delivery to customers, contractors not under the required control, partners, or other third parties, and customer-facing hosted services, is Public Use even when access is private, authenticated, or governed by an NDA. Only use confined to the same legal organisation or persons under its control may fall within the internal-use exception.
- Derivative Database: conservatively, a machine-readable MATSim network containing a substantial transformed extraction of OSM is treated as an ODbL Derivative Database unless a documented, use-specific review concludes otherwise. Classify every other artifact separately as Database, Derivative Database, Collective Database, Produced Work, or non-OSM artifact; do not infer one classification for the whole bundle.
- Share-alike: any Derivative Database that is Publicly Used must be under ODbL.
- Attribution (ODbL 4.2/4.3 and OSMF guidance): public database exports must include attribution to OpenStreetMap and either the ODbL text or a direct link, placed in the exported database or adjacent machine-readable metadata and repeated in relevant documentation; upstream notices must be preserved. Publicly displayed Produced Works (maps, reports, visualizations) must carry visible attribution appropriate to their presentation, such as `© OpenStreetMap contributors` linking to `https://www.openstreetmap.org/copyright`.
- Access offer (ODbL 4.6): for each Public Use of a Derivative Database, or of a Produced Work made from one, recipients must be offered either the entire machine-readable Derivative Database, or a file containing all alterations or the method of making them, including additional contents accounting for all differences. Internet distribution of that offer must be free of charge; physical distribution may cost no more than reasonable production cost. Attribution and license notices do not satisfy this offer. Unlike GPLv2 section 3, ODbL specifies no offer-period duration; do not import GPL terms here.
- Technical restrictions: product EULAs must carve ODbL-covered databases out of conflicting no-copy, no-redistribution, or technical-control provisions. If a covered database is distributed in restricted form, ODbL 4.7's unrestricted parallel-copy requirement must be evaluated.

Provenance metadata must be sufficient to support the ODbL 4.6 alteration-method option. Required provenance fields:

- exact source URL or provider, extract identifier, and source file digest
- importer version or commit hash
- complete effective configuration: way rules, access profile, cleaning and simplification parameters
- target CRS and transformation identifier
- additional contents incorporated into the derived database, if any
- output schema/version
- location where the access offer is published

If the recorded provenance cannot account for all differences from the source extract, the release process must offer the complete Derivative Database instead.

`matsim-models` writes source/provenance attributes into generated network metadata when configured:

- source name: `OpenStreetMap`
- source license: `ODbL-1.0`
- attribution text
- input file name, extract identifier, and source digest
- importer version
- import timestamp (injectable `Instant`/`Clock` for deterministic tests)
- target CRS
- simplification mode
- tag-retention mode

## Non-Goals

- Running MATSim simulations
- Wrapping `org.matsim.*` APIs
- Depending on Pt2MATSim, MATSim, or MATSim contrib Maven artifacts
- Providing HAFAS conversion in v1
- Providing full OSM route-relation-to-transit-schedule conversion in v1
- Providing shapefile export in v1

## Pipeline Overview

```text
OSM extract
  -> OSM parser
  -> OSM feature store
  -> network builder
  -> query indexes and metadata tables
  -> network simplifier/cleaner
  -> MATSim-compatible Network

GTFS feed(s)
  -> GTFS parser
  -> combined feed set
  -> unmapped TransitSchedule + Vehicles
  -> stop/link candidate lookup on OSM-derived network indexes
  -> route mapping
  -> mapped TransitSchedule + modified Network + Vehicles + reports
```

The key design decision is that Phase 1 does not throw away information that Phase 2 needs. The emitted MATSim network can stay compact, but the import result keeps sidecar metadata and indexes for mapping.

## Upper-Level Task Phases

These are the assignment-level phases for implementation planning. Each phase should become its own implementation plan or subagent work package before code execution.

### Phase 0: Model And IO Repairs (Prerequisite)

Repair the existing model/IO layer so later phases can represent their outputs. Full deliverable list in "Phase 0: Model And IO Repairs (Prerequisite)" below; summary:

- `TransitRoute` link-sequence model + schedule writer/reader `<route>` support + MATSim wire-format corrections (`linkRefId`, child `transportMode`)
- `VehicleType` model, XML support, referential validation
- `Scenario` vehicles field + bundle save
- duplicate-key guards on transit containers
- empty-`allowedModes` preservation on `Link`
- `TransitStopFacility.parentId`
- `XmlSupport` attribute codec extension for `DisallowedNextLinks` (DOM path, both directions)
- `NetworkValidator` artificial-link options
- deterministic ID codec utility
- streaming StAX writers for large outputs

### Phase 1A: OSM License, Provenance, And Import Contract

Define the OSM/ODbL product boundary, provenance metadata model, import result shape, and configuration contract. This phase does not build routing behavior yet; it makes sure every downstream artifact can carry source/license context.

Deliverables:

- `OsmImportConfig` (including injectable clock for deterministic timestamps)
- `OsmImportResult`
- OSM provenance metadata value objects (source digest, importer version, effective configuration)
- attribution/license metadata writer behavior
- tests proving OSM-derived outputs preserve attribution/provenance

### Phase 1B: OSM XML Parsing And Tag Retention

Parse OSM XML into source records without deciding yet what becomes a MATSim link. Preserve raw tags, source ids, relation membership (with member order and roles), and coordinates in WGS84/projected CRS form.

Deliverables:

- node, way, relation, and relation-member records
- configurable raw tag retention (output-side switch; classification tags always parsed)
- compressed/plain XML stream support via existing gzip IO utilities
- parser warnings for malformed or unsupported elements
- tests with synthetic OSM fixtures, including `.osm.gz` parity

### Phase 1C: OSM Way Rules, Access, Speeds, And Directed Links

Convert source ways into directed network links using explicit rules for road, rail, ferry, access hierarchy, oneway (including mode exceptions and implied oneways), freespeed, capacity, and lane resolution.

Deliverables:

- `OsmWayRule`
- hierarchical mode/access resolver with three-state access and directional overrides
- oneway resolver with implied defaults and `oneway:<mode>` exceptions
- speed resolver with symbolic value mapping
- lane resolver with derivation, `both_ways`, conflict warnings
- directed link creation
- source OSM id to produced link id mapping
- tests for highway, railway, ferry, oneway, maxspeed, and access behavior

### Phase 1D: Lane, Intersection, Stop, And Relation Hints

Extract metadata that the cleaned MATSim network cannot fully express but later transit mapping needs.

Deliverables:

- lane hint extraction for `lanes`, directional lanes, bus/psv lanes (prefix and suffix schemes), per-lane general access, and turn lanes (soft evidence)
- node-level intersection lane hints (including traffic-signal detection from source node tags)
- geometry-discriminated OSM stop/platform/station hints (point/line/area)
- stop-area relation parsing as first-class metadata
- route and route_master relation hints with member order, duplicates, and roles
- type=restriction relation parsing inputs for turn restrictions (normalized model input)
- barrier passability hints
- tests for lane/stop/relation/barrier hint extraction

### Phase 1E: Geometry Preservation And Routing Node Simplification

Separate source OSM nodes, shape points, and actual routing nodes. Preserve link polylines without forcing every shape point into the MATSim network by default.

Deliverables:

- `OsmGeometryStore`
- `geometryMode` handling
- routing-node retention rules (including restriction via-node precomputation and barrier nodes)
- compact network plus link geometry sidecar
- optional materialized geometry-node mode
- length computations from full polylines
- tests for simplification and shape preservation with a shape-only interior node

### Phase 1F: Turn Restrictions, Cleaning, And Query Indexes

Turn the imported network into a cleaned MATSim-ready network and build the query structures Phase 2 depends on.

Deliverables:

- local `DisallowedNextLinks` value object and XML codec support on the DOM attribute path (both directions), with class-hint fixture tests
- OSM restriction conversion per the normalized model (prohibitive, mandatory, except, mode-suffixed, via-way)
- restriction translation after final link ids, plus post-cleaning restriction validation
- network cleaner with quarantine semantics and sidecar compaction
- per-segment uniform-grid link/node/stop spatial indexes with deterministic ordering
- mode/link, OSM id/link, lane hint, and turn restriction indexes
- tests for cleaning, turn restrictions (including negative assertions), nearest-link queries, and nearest-stop queries

### Phase 2A: GTFS Import And Feed Combining

Parse one or more GTFS feeds, preserve feed identity, validate required files, and expose feed-level query views.

Deliverables:

- `GtfsImporter`
- `GtfsFeed`
- `GtfsFeedSet`
- GTFS record model
- feed id prefixing
- service date query support
- tests for parsing, validation, multiple feeds, and id collisions

### Phase 2B: GTFS Schedule And Vehicle Production

Convert combined GTFS feeds into unmapped MATSim-compatible transit schedules and vehicle definitions.

Deliverables:

- `GtfsTransitScheduleBuilder`
- service selection
- frequency expansion
- route grouping
- stop CRS transformation
- `VehicleDefinitions` generation
- departure vehicle id generation
- tests for schedules, departures, frequencies, vehicles, and XML round trips

### Phase 2C: Stop Candidate Scoring Using Phase 1 Indexes

Use the cleaned network, spatial index, OSM stop hints, lane hints, and access/mode metadata to score candidate links for each GTFS stop.

Deliverables:

- `LinkCandidateSelector`
- candidate scoring model
- stop hint matching
- lane hint scoring
- side-of-road/platform hint scoring when available
- artificial loop candidate behavior for unmapped stops
- tests proving OSM hints improve candidate selection

### Phase 2D: Transit Route Mapping And Artificial Links

Map each transit route onto the cleaned network using pseudo-routing, least-cost paths, turn restrictions, and artificial links where needed.

Deliverables:

- `TurnRestrictedRouter`
- `PseudoRouteMapper`
- `ArtificialTransitLinkFactory`
- route link sequence creation
- path-cost threshold handling
- tests for pseudo-routing, turn-restricted routing, disconnected paths, and artificial links

### Phase 2E: Child Stop Facilities, Cleanup, And Reports

Finalize mapped schedules and make diagnostics visible to CityModeler.

Deliverables:

- child stop facility creation with collision-checked ids and fixed metadata keys
- mapped schedule construction as new instances (inputs unmodified)
- transit network cleanup
- plausibility checker
- mapping diagnostics and warnings
- final XML round-trip tests

### Phase 3: CityModeler Integration Boundary

Wire the two-phase API into CityModeler without adding MATSim runtime, Pt2MATSim, or GPL dependencies.

Deliverables:

- CityModeler import workflow contract
- progress/report DTOs suitable for UI display
- attribution/provenance display requirements and ODbL access-offer workflow hooks
- generated bundle layout expectations (network + schedule + vehicles as separately notifiable components)
- integration tests or smoke tests using synthetic fixtures

## Phase 1: OSM Network Import

### Goals

Phase 1 produces two things:

- A MATSim-compatible cleaned network suitable for routing and simulation.
- A queryable OSM import result containing tags, geometry, lane hints, stop hints, relation hints, and provenance needed by later transit mapping.

### Public API Shape

```java
OsmImportResult osm = new OsmNetworkImporter().read(osmConfig);

OsmNetworkBuildResult network = new OsmMatsimNetworkBuilder().build(
    osm,
    networkConfig
);
```

`OsmNetworkBuildResult.queryIndex()` provides the query structures; a standalone `NetworkQueryIndexBuilder` exists only for externally loaded networks.

### Packages

```text
com.citymodeler.matsim.models.osm
  OsmNetworkImporter
  OsmImportConfig
  OsmImportResult
  OsmElementId
  OsmTagSet
  OsmImportIssue

com.citymodeler.matsim.models.osm.model
  OsmNodeRecord
  OsmWayRecord
  OsmRelationRecord
  OsmRelationMemberRecord

com.citymodeler.matsim.models.osm.network
  OsmMatsimNetworkBuilder
  OsmNetworkBuildConfig
  OsmNetworkBuildResult
  OsmWayRule
  OsmModeAccessResolver
  OsmSpeedResolver
  OsmLaneResolver
  OsmTurnRestrictionReader
  OsmNetworkSimplifier
  OsmNetworkCleaner
  OsmGeometryStore
  OsmStopHintExtractor
  OsmLaneHintExtractor

com.citymodeler.matsim.models.network.index
  NetworkQueryIndex
  NetworkQueryIndexBuilder
  LinkSpatialIndex
  NodeSpatialIndex
  StopHintIndex
  LaneHintIndex
```

### Input Formats

v1 should support `.osm` XML first because it is simple and dependency-light. `.osm.gz` is useful if existing IO utilities already support compressed streams. `.pbf` can be added later behind an Apache/BSD-compatible parser dependency after the XML path is correct.

### OSM Tags To Preserve

Phase 1 should preserve the raw tags needed for later inspection and mapping. Raw tag retention is configurable because it increases output size.

Core road/network tags:

- `highway`
- `railway`
- `route`
- `public_transport`
- `amenity`
- `service`
- `access`
- `motor_vehicle`
- `vehicle`
- `psv`
- `bus`
- `taxi`
- `bicycle`
- `foot`
- `oneway`
- `junction`
- `bridge`
- `tunnel`
- `layer`
- `name`
- `ref`
- `operator`
- `network`
- `surface`
- `tracktype`
- `maxspeed`
- `maxspeed:forward`
- `maxspeed:backward`

Lane and turn-lane tags:

- `lanes`
- `lanes:forward`
- `lanes:backward`
- `lanes:bus`
- `lanes:psv`
- `bus:lanes`
- `psv:lanes`
- `turn:lanes`
- `turn:lanes:forward`
- `turn:lanes:backward`
- `change:lanes`
- `placement`
- `width`

Transit stop/platform tags:

- `highway=bus_stop`
- `public_transport=platform`
- `public_transport=stop_position`
- `railway=station`
- `railway=halt`
- `railway=tram_stop`
- `amenity=bus_station`
- `bus=yes/designated`
- `tram=yes/designated`
- `train=yes/designated`
- `subway=yes/designated`
- `ferry=yes/designated`
- `wheelchair`
- `shelter`
- `bench`
- `zone`

Relation tags:

- `type=route`
- `type=route_master`
- `type=restriction`
- `restriction`
- `restriction:*`
- route values for `bus`, `trolleybus`, `tram`, `light_rail`, `subway`, `train`, `rail`, `monorail`, `funicular`, `ferry`

Unknown preserved tags are allowed under a namespace such as `osm:tag:<key>` if `keepRawTags` is enabled.

### Way Selection And Mode Rules

The network builder converts OSM ways according to configurable `OsmWayRule`s. Each rule maps an OSM key/value to:

- hierarchy level
- allowed MATSim modes
- default lanes by direction
- default freespeed
- default capacity per lane
- default oneway behavior
- whether the way is routable, transit-only, or metadata-only

Default v1 conversion should cover at least:

- `highway=motorway`, `trunk`, `primary`, `secondary`, `tertiary`
- `highway=unclassified`, `residential`, `living_street`, `service`
- `highway=motorway_link`, `trunk_link`, `primary_link`, `secondary_link`, `tertiary_link`
- `highway=busway`
- `railway=rail`, `light_rail`, `subway`, `tram`, `monorail`, `funicular`
- `route=ferry` ways and relations assembled per the ferry rules below

#### Access Resolution

Access is resolved through the OSM access hierarchy, not a flat override. Resolution order per way and per mode:

1. Way-rule jurisdictional defaults for the way's class.
2. Ancestor-to-descendant explicit overrides following `access` → `vehicle` → `motor_vehicle` → `psv` → `bus` (and `taxi`, `hgv`, `bicycle`, `foot` where relevant). The most specific applicable key wins; a parent key sets the default for all children that are not explicitly tagged.
3. Directional overrides `*:forward` / `*:backward` (for example `motor_vehicle:forward=no`).
4. Conditional tags (`*:conditional`) are not evaluated in v1. Their presence forces the conservative outcome below and records a warning with the tag key.

Access values map to three states, not a boolean: `FORBIDDEN` (`no`, `never`), `LEGALLY_RESTRICTED` (`private`, `destination`, `customers`, `delivery`, `agricultural`, `forestry`), `ALLOWED` (`yes`, `designated`, `permissive`). Network links are created only when the applicable state for a required mode is `ALLOWED`; `LEGALLY_RESTRICTED` values produce a warning and, unless config keeps them (`keepRestrictedAccessWays=false` default), are excluded.

The access keys participating in the algorithm are always parsed regardless of `keepRawTags`, because the resolver needs them.

A link's final resolved mode set distinguishes physical-graph compatibility from vehicle-specific legal access. When Phase 2 routes a schedule mode over network mode `car` links, the router must re-check the resolved bus/PSV access profile of the source OSM segment; a way with `motor_vehicle=yes` and `bus=no` is car-compatible but not bus-legal, and must be excluded from bus routing even though the emitted MATSim link carries `car`.

#### Oneway Resolution

`oneway` parsing accepts `yes`/`true`/`1` (forward only), `-1`/`reverse` (backward only), `no`/`false`/`0` (bidirectional), and `alternating`/`reversible` (unsupported in v1: treated conservatively as forward-only with a warning issue).

Implied oneway defaults are applied after explicit tags: `junction=roundabout` and `highway=motorway` default to forward-oneway unless explicitly `oneway=no`; `highway=motorway_link` follows the parent motorway default.

Mode-specific oneway exceptions are applied last and override the generic value for that mode: `oneway:bus`, `oneway:psv`, `oneway:taxi`, `oneway:bicycle` (for example `oneway=yes` + `oneway:bus=no` produces a reverse bus-only link). A mode's oneway exception cannot grant a direction forbidden by directional access (`oneway:bus=no` plus `motor_vehicle:backward=no` without bus access still yields no reverse bus link).

#### Ferry Rules

Ferry conversion is separate from road/rail rules:

- ways carrying `route=ferry` are converted with mode `ferry` plus configured passenger modes;
- `route=ferry` relations are assembled as ordered connected member chains; members that do not connect to the chain produce warnings;
- `amenity=ferry_terminal` nodes/areas become stop hints and must connect to the road/pedestrian network for path continuity;
- allowed passenger modes are derived from the resolved access vector rather than a single undifferentiated `ferry` mode.

#### Barriers

`barrier=*` nodes on routable ways are preserved and always retained as routing nodes. Passability is computed per mode from barrier-specific defaults (for example `bollard`, `gate`, `lift_gate`, `bus_trap`, `block`), followed by node-level access overrides (`access`, `motor_vehicle`, `bus`, ...). Unknown barrier types use a configurable conservative default (`barrierUnknownDefault=FORBIDDEN` for cars, allowed only for configured transit modes if `barrierUnknownAllowsTransit=true`). A barrier that forbids a mode splits nothing but records per-mode passability used by routing and candidate scoring; a bus trap must not block buses.

### Speeds, Capacities, And Lanes

OSM provides incomplete traffic-flow data. The builder must make defaults explicit and auditable.

Speed resolution order:

- directional `maxspeed:forward` / `maxspeed:backward`
- `maxspeed`
- way-rule default freespeed

Accepted maxspeed formats: numeric km/h, numeric with `mph`, and configured symbolic values. Nonnumeric handling: `none` and `walk` map to configured finite simulation speeds (`maxspeedNoneKph`, `maxspeedWalkKph`); unrecognized symbolic values fall back to the rule default and emit one warning per way. `maxspeed:conditional` and lane-specific speed limits are not evaluated in v1; their presence emits a warning.

Capacity semantics: all rule capacities are vehicles per hour, and generated networks use a fixed 3600-second capacity period for v1. Serialized link capacity is total directional capacity:

```text
link capacity = directional effective lanes (permlanes) × capacity per lane
```

Link `length` is meters; `freespeed` is m/s. The writer serializes `capperiod="01:00:00"` on `<links>` once supported; until then the 3600-second convention is documented as the v1 invariant.

Lane resolution order, per direction:

1. directional `lanes:forward` / `lanes:backward`
2. shared-center `lanes:both_ways` (added to each direction's approach capacity but not doubled)
3. if exactly one of forward/backward is tagged and total `lanes` is present, derive the missing direction as `lanes − tagged − both_ways`
4. if `lanes` alone is present on a bidirectional way, split evenly; an odd remainder assigns the extra lane to the forward direction and records a warning
5. way-rule default lanes by direction

When `forward + backward + both_ways != lanes` with all present, the resolver records a conflict warning, uses the tagged directional values, and emits per-direction lane hints only.

Bus/PSV lane tags are parsed in both prefix and suffix schemes with direction variants: `lanes:bus`, `lanes:psv`, `lanes:bus:forward`, `lanes:bus:backward`, `lanes:psv:forward`, `lanes:psv:backward`, `bus:lanes`, `psv:lanes`, `bus:lanes:forward`, `bus:lanes:backward`, `psv:lanes:forward`, `psv:lanes:backward`, plus per-lane general access `access:lanes`, `vehicle:lanes`, `motor_vehicle:lanes`. Pipe-separated per-lane values are read left-to-right in the direction of travel; blank entries inherit from the way-level state; value counts are validated against the directional lane count with warnings on mismatch. `designated` in a `*:lanes` pattern marks a designated lane but exclusivity is inferred only by combining per-lane general access (`bus:lanes=|designated` plus `access:lanes=yes|no` means exclusive; without `access:lanes` it is shared).

A way-level `bus=designated|yes` or `psv=designated|yes` on a link whose resolved modes include `bus` sets `busLanes = totalLanes` and `dedicatedTransitLane=true` for the bus-legal direction.

`turn:lanes` and variants are soft evidence only. They record lane indications and must never become hard movement prohibitions; hard rejections come exclusively from resolved access, oneway rules, barriers, and restriction relations. `none`, `through`, merge values, and semicolon alternatives are parsed without conversion into prohibitions.

Additional lane hints retained at each link/node:

- total lanes by direction
- bus/psv lane count or lane-designation pattern
- turn-lane strings by direction
- whether a link has dedicated transit lanes
- approximate approach-lane count at intersections
- conflicting/incomplete lane tags as warnings

MATSim's base network stores link lane count, freespeed, and capacity. More detailed OSM lane/turn-lane information is kept in sidecar attributes/indexes for future lane modeling and route mapping, not forced into base network semantics. The explicit design decision on the existing Lanes file format: turn-lane connectivity (`turn:lanes` where derivable) may additionally be emitted as a Lanes file using `Lane.toLinkIds`, but node-level intersection hints and bus/psv lane counts remain sidecar-only because the Lanes format cannot represent them; v1 keeps everything sidecar-only by default with Lanes-file emission as a config option.

### Node-Level Lane Information

At each created routing node, collect an `IntersectionLaneHint`:

- incoming links and outgoing links
- OSM ids of participating ways
- approach lane counts by incoming link
- turn-lane tags on incoming ways
- allowed movement hints inferred from turn restrictions and oneway/access tags
- traffic signal presence from `highway=traffic_signals`
- stop/yield/crossing hints when present

This is not a complete lane model. It is enough for later mapping and plausibility checks to know whether a bus is likely to be able to turn, whether a stop is near a complex intersection, and whether detailed lane modeling may be needed.

### Stop Information For Later Mapping

OSM transit stops are extracted even when GTFS will be the schedule authority.

`OsmStopHint` records are geometry-discriminated, because platforms can be nodes, ways, or areas:

- OSM element id, type, and geometry class: `POINT` (node), `LINE` (linear way), `AREA` (closed area way)
- full projected geometry, plus a derived representative point
- stop kind: bus stop, stop position, platform, station, halt, tram stop, ferry terminal (`amenity=ferry_terminal` included), `public_transport=station`, `railway=platform`, `highway=platform`
- name/ref/operator/network tags
- served mode hints from `yes`/`no` service indicators (`bus`, `tram`, `train`, `subway`, `ferry`); `designated` and other access-style values are preserved verbatim as access evidence with a warning, not treated as service indicators
- nearby way ids and link ids after network creation
- side-of-road estimate computed from the full geometry, not the representative point alone
- parent relation ids

Stop-area relations (`type=public_transport` with `public_transport=stop_area`, and `stop_area_group`) are parsed as first-class metadata with member roles. They are the primary mechanism associating stop positions with platforms and outrank proximity matching in Phase 2 scoring. Membership and the relation graph are indexed.

Route-relation hints preserve member order, duplicates, and roles. PTv2 roles (`stop`, `stop_entry_only`, `stop_exit_only`, `platform`, `platform_entry_only`, `platform_exit_only`) are normalized without discarding unknown roles; ordered path-way membership is preserved. Route-master ancestry is derived through stop → route → route_master; a route master contains route relations, not stops.

`route=rail` is not treated as a passenger-service route value; `train`, `tram`, `light_rail`, `subway`, `monorail`, and `funicular` are PT service routes, and `route=railway`/`route=tracks` remain infrastructure metadata only.

Phase 2 can use these hints to improve stop-link candidate scoring, especially where GTFS stop coordinates are slightly off-road or represent platforms rather than stop positions.

OSM route relations are metadata in v1, not a schedule source. They can provide stop/way association hints but GTFS remains authoritative for departures and route profiles.

### Topology Contract

Graph connectivity derives exclusively from shared OSM node identity. Geometrically crossing or coincident ways are not connected unless they share a source node. Nearby endpoints are never auto-snapped. `bridge`, `tunnel`, `layer`, and `level` are validation context, never connectivity inputs. Any synthetic connection (for example a future repair operation) must be a separate, explicitly configured and reported operation.

Stop-position nodes are retained as routing nodes only when they are actual node members of a converted routable way. Platform geometry stays external to road topology and is associated through stop areas, route roles, or scored snapping in Phase 2. Retaining a routing node merely because a platform is nearby is forbidden; it can anchor the platform to the wrong carriageway or fabricate a junction OSM does not contain.

### Geometry Preservation: Shape Nodes Vs Routing Nodes

OSM ways contain many points that are geometry-only. MATSim networks are faster when only true routing decision points become nodes.

Phase 1 separates:

- Source OSM nodes: every node read from the OSM extract.
- Shape points: intermediate geometry points used to preserve link polyline shape.
- Routing nodes: nodes retained in the MATSim network because links branch, ways end, restrictions apply, stops need anchoring, or config forces retention.

Config option `geometryMode`:

- `ROUTING_NODES_ONLY`: compact network; intermediate geometry stored only in sidecar geometry table.
- `PRESERVE_AS_LINK_GEOMETRY`: compact network plus per-link polyline sidecar; recommended default.
- `MATERIALIZE_GEOMETRY_NODES`: every geometry point becomes a MATSim node/link; useful for visualization but slower.

`PRESERVE_AS_LINK_GEOMETRY` is the v1 default. It keeps MATSim routing fast while giving CityModeler accurate road shapes for display and stop-distance calculations.

Routing-node retention rules:

- keep way endpoints
- keep intersections and branch points
- keep nodes involved in turn restrictions: the restriction via-node set is computed from `type=restriction` relations before link creation and fed to the simplifier (via-way restrictions retain all nodes of the via-way chain)
- keep barrier nodes on converted ways
- keep stop-position nodes that are actual members of converted routable ways when `preserveTransitStopNodes=true`; platform proximity alone never creates a routing node
- keep nodes explicitly requested by id
- optionally keep sharp bends above an angle threshold to avoid extremely long straight links

All length, bounding-box, nearest-point, bearing, and side-of-road computations use the full projected directed polyline when sidecar geometry exists; the endpoint chord is never a substitute. Reverse-direction links store the reversed point order.

### Turn Restrictions

OSM restriction relations are converted to MATSim-compatible link attributes named `disallowedNextLinks`. Conversion happens in a defined order (see the topology pipeline in Network Cleaning): a normalized restriction model is built from source relations first; after final cleaned link ids are stable, normalized movements are translated to disallowed sequences.

`matsim-models` adds an independent value class:

```java
DisallowedNextLinks
```

It is not a MATSim class and has no `org.matsim` dependency. The XML wire contract is explicit:

- attribute name: `disallowedNextLinks`
- the exact MATSim-compatible class-hint string targeted: `org.matsim.core.network.turnRestrictions.DisallowedNextLinks` (current public MATSim); the reader additionally accepts the historic `org.matsim.core.network.DisallowedNextLinks` hint as an alias
- value shape: JSON `Map<String, List<List<String>>>`, mode → list of disallowed next-link-id sequences, for example `{"car":[["linkA","linkB"]]}`
- the starting link is implicit and never appears inside its own stored sequences
- deterministic key and list ordering on write
- malformed values: reader records a warning, drops the malformed entry, and continues
- the attribute path uses the DOM-based `XmlSupport` attribute codec (the single write/read path for network attributes); Jackson `AttributesSerializer`/`AttributesDeserializer` are updated only as secondary consistency

Literal XML fixture tests assert the exact class-hint string and payload; `toString()` fallback serialization is never used for this value.

Normalized source restriction model, built from `type=restriction` relations (and the legacy `type=restriction:<mode>` spelling):

- all members preserved with ordinal, type, id, and role; duplicates preserved
- `from` way resolved to the directed produced link ending at the via location, in the from-way's direction of travel
- `to` way resolved to the directed produced link starting at the via location
- `via` is either exactly one node, or an ordered connected chain of via ways; multi-member cardinality exceptions `no_entry` (multiple `from`) and `no_exit` (multiple `to`) are honored
- `restriction:<mode>` keys restrict only that mode; a bare `restriction` restricts the configured default set (v1: `car`, plus `bus` only when both resolved links carry bus access)
- `except=*` is parsed as a semicolon-separated mode list; excepted modes are removed from the restriction's mode set; unknown except values warn
- prohibitive values (`no_left_turn`, `no_right_turn`, `no_straight_on`, `no_u_turn`) forbid the described movement: the sequence from-link → to-link becomes one disallowed sequence
- mandatory values (`only_*`) forbid every alternative: for each outgoing link at the via location other than the `to` link, a disallowed sequence from-link → alternative is recorded; `only_u_turn` additionally restricts returning on the from-link itself
- conditional restrictions (`restriction:conditional`, `*:conditional` variants) are not evaluated in v1: recorded with a warning, not converted
- restrictions whose `from`/`via`/`to` members are absent from the cleaned network record `UNRESOLVED_TURN_RESTRICTION`; via-way chains whose members are not mutually connected record `UNSUPPORTED_VIA_WAY`

After cleaning, a final restriction validator verifies every stored sequence: each subsequent link id exists, is topologically contiguous with the previous, and permits the restricted mode. Violations are dropped with warnings, never written as corrupt attributes.

### Fast Queryability

Phase 2 needs fast nearest-link, nearby-stop, and route-path queries. Build indexes as part of `OsmNetworkBuildResult` or through a deterministic index builder.

Required indexes:

- link spatial index indexing each polyline segment into the grid cells that segment traverses (not the aggregate bounding box), with exact point-to-polyline distance recomputed at query time
- node spatial index by coordinate
- stop hint spatial index
- link id to source OSM way id(s)
- OSM way id to produced link ids by direction
- mode to link ids
- node to outgoing/incoming links by mode
- turn restriction index by mode and previous-link suffix
- lane hint index by node/link

Implementation contract for v1:

- simple uniform grid spatial index, dependency-free, adequate for projected city/regional networks
- half-open cell math that is correct for negative coordinates; maximum visited-cell cap per query
- query results deduplicate link ids and sort by `(distance, linkId)`; equal-distance ties are impossible after the id tiebreak
- read-only after build; coordinates in projected CRS meters
- query methods with distance cutoffs and max-result limits protect UI responsiveness
- indexes store compact ids/ordinals, not repeated `Link` object references
- the index carries the CRS identifier of the network it was built from; queries against mismatched coordinates are rejected

Candidate query examples:

```java
List<LinkCandidate> links = index.nearestLinks(point, modes, radiusMeters, maxResults);
List<OsmStopHint> stops = index.nearestStopHints(point, mode, radiusMeters);
List<Link> outgoing = index.outgoingLinks(nodeId, mode);
```

### Network Cleaning

Cleaning is the final ordered stage of a single topology pipeline. The full pipeline order is normative:

1. select ways and apply access/oneway/mode resolution
2. compute decision nodes (retention rules above) and build directed links
3. normalize metrics (length from polyline, freespeed, permlanes, capacity)
4. build the normalized restriction model
5. clean topology per mode
6. translate restrictions to disallowed sequences against final link ids
7. validate restrictions and sidecars
8. compact sidecars to surviving ids
9. build indexes

Cleaning steps within stage 5:

- remove links with invalid geometry, missing endpoints, or non-finite metrics
- component policy per configured mode: keep only the largest weakly connected component by link count; smaller components are quarantined, not silently deleted
- quarantined components: removed from the primary network, retained in `OsmNetworkBuildResult.quarantinedComponents()` with their sidecar entries; transit-tagged components stay quarantined (not dropped) even with `keepWaysWithTransit=true`, and are dropped only when config allows it, always with issues carrying source OSM ids
- routable-subnetwork enforcement for configured modes operates on the surviving graph
- duplicate identity is `(source OSM way id, direction, segment index)`; parallel links from different source ways are never duplicates
- zero-length links are removed unless they are artificial/loop connectors created by a later phase, which are exempt by their `pt_` id prefix
- sidecar tables (geometry, hints, id mappings) always reference only surviving ids after compaction

The cleaner must never silently discard a way carrying transit tags, stop references, or route relation membership. It can drop such ways only with an issue entry that CityModeler can surface.

### Phase 1 Result

`OsmNetworkBuildResult`:

- cleaned `Network`
- `NetworkQueryIndex`
- `OsmGeometryStore`
- OSM id mapping tables
- stop hint table
- lane hint table
- turn restriction report
- cleaning report
- provenance/license metadata
- warnings and fatal issues

## Phase 2: GTFS Combining And Transit Mapping

### Goals

Phase 2 consumes:

- one or more GTFS feeds
- Phase 1 cleaned network
- Phase 1 query indexes and stop/lane/geometry sidecars

It produces:

- unmapped and mapped `TransitSchedule`
- `VehicleDefinitions`
- modified network with artificial transit links where needed
- departure records
- mapping and plausibility reports

### Public API Shape

Mapping consumes the Phase 1 build result as a unit — network, indexes, and sidecars are guaranteed mutually consistent because they come from one build. Ownership semantics are explicit: mapping never mutates its inputs; it constructs and returns new `TransitSchedule` and `Network` instances (build-new semantics), leaving the unmapped build result intact for before/after reports.

```java
OsmImportResult osm = new OsmNetworkImporter().read(osmConfig);
OsmNetworkBuildResult network = new OsmMatsimNetworkBuilder().build(osm, networkConfig);

GtfsFeedSet feeds = new GtfsImporter().read(gtfsConfig);
GtfsTransitBuildResult build = new GtfsTransitScheduleBuilder().build(feeds, buildConfig);

TransitMappingResult mapped = new TransitNetworkMapper().map(
    build.schedule(),
    network,
    mappingConfig
);
```

A standalone `NetworkQueryIndexBuilder` exists only for externally loaded networks (a network read from file rather than built); for built networks the index comes from `OsmNetworkBuildResult.queryIndex()`.

Existing writers remain responsible for XML output, with wire-format corrections as Phase 0 prerequisites:

- `TransitScheduleXmlWriter`: emits MATSim-documented fields (`linkRefId`, child-element `transportMode`, `<route><link refId/></route>`)
- `NetworkXmlWriter` / `NetworkXmlReader`: DOM attribute path handles `DisallowedNextLinks` per the wire contract
- `VehiclesXmlWriter`: emits `vehicleType` definitions with capacities

### GTFS Coverage

v1 supports static GTFS feeds supplied as zip files or folders.

Required files:

- `stops.txt`
- `routes.txt`
- `trips.txt`
- `stop_times.txt`

Optional files supported in v1:

- `agency.txt`
- `calendar.txt`
- `calendar_dates.txt`
- `frequencies.txt`
- `shapes.txt`

Files intentionally ignored in v1:

- fares
- transfers
- pathways
- levels
- translations
- feed_info, except optional metadata passthrough if cheap

GTFS Flex and deviated fixed-route rows are skipped explicitly, never silently: stop_times rows carrying `location_group_id`, `location_id`, or `start/end_pickup_drop_off_window` produce one aggregate warning per feed and per route; trips whose stop_times are all skipped produce a per-trip warning and no departure. Trips with `continuous_pickup/drop_off` other than unset/1 import as fixed-route with a warning that demand-responsive deviations are not represented. The parser tolerates a missing `route_type` column (conditionally required in current GTFS) with a warning and a `pt` default.

The builder records each feed's `agency_timezone` and each route's `agency_id` as metadata. GTFS times are local wall-clock times in the feed timezone and are stored as-is; no timezone conversion is applied. `trips.wheelchair_accessible` and `bikes_allowed` are stored as trip metadata.

### GTFS Combining

`GtfsFeedSet` supports multiple feeds. Each feed gets a stable feed id: explicitly from config, or derived from filename/folder name. Derived ids are sanitized to `[A-Za-z0-9_-]+` (colons and path characters replaced); collisions resolve by suffixing `-2`, `-3`, ...; duplicate derived ids in one build are rejected.

Object ids are always prefixed — prefixing is mandatory and not configurable off:

- `feedId:stopId`
- `feedId:routeId`
- `feedId:tripId`

Feed ids are sanitized so the `:` delimiter is unambiguous. Config may display unprefixed ids in reports, but the `TransitSchedule` always uses prefixed ids.

The feed set supports query views:

- by feed id
- by agency id/name
- by GTFS route type
- by resulting MATSim transport mode
- by service date

Conflicting ids across feeds are not merged by default. Optional stop de-duplication can be added later, but v1 prefers preserving feed identity over clever merging.

### Stops Model

Only stops with `location_type` 0 or empty (platforms) become `TransitStopFacility` objects. Rows with `location_type` 1–4 (stations, entrances/exits, generic nodes, boarding areas) are kept as metadata:

- a platform's GTFS parent-station id is stored as `parentId` metadata on its facility
- station coordinates are a fallback for a platform lacking coordinates, with a warning
- stops.txt rows with empty lat/lon that become facilities are a fatal feed issue
- stop_times referencing a `stop_id` that is not a platform is a fatal feed issue

The `parentStopId` in the child-stop convention refers to the GTFS platform's own stop id; the GTFS parent station, when present, is stored as `parentId` metadata.

### Service Selection And Departures

Service materialization per feed: `calendar.txt` weekday/date-range rules first, then `calendar_dates.txt` exceptions applied with `exception_type` 1 = added, 2 = removed. A feed containing only `calendar_dates.txt` is fully supported. A feed containing neither file fails with a fatal issue unless config sets `assumeAlwaysActive=true`, in which case all trips are treated as active for every queried date and a warning is recorded.

The builder supports:

- explicit date, `YYYYMMDD` (a GTFS service date, interpreted per feed in its own service-day sense — no cross-timezone conversion of times is performed)
- `dayWithMostTrips`: counts frequency-expanded departures per date, not raw trips
- `dayWithMostServices`: counts distinct active `service_id`s per date
- `all`: emits every active service date (see below)

Ties for `dayWithMostTrips`/`dayWithMostServices` resolve to the earlier date. With multiple feeds, counts aggregate across feeds after per-feed exception resolution.

#### Departure Construction

For schedule trips (not referenced in `frequencies.txt`), exactly one departure per trip:

- departure time is the trip's first stop_time `departure_time`, in seconds from the start of the GTFS service day
- GTFS times at or after `24:00:00` are kept as seconds beyond 86400 and are never wrapped
- profile offsets are `arrival_i − departure_0` and `departure_i − departure_0`, relative to the trip's own first stop_time departure
- empty arrival/departure fields on non-timepoint stops are linearly interpolated between the nearest surrounding stop_times that carry times
- a trip whose first or last stop_time lacks times is rejected with a warning
- offsets must be non-negative and non-decreasing; violations are fatal feed issues
- stop_times with `pickup_type=1` or `drop_off_type=1` remain in the route profile with their flags as stop metadata; they still contribute offsets and candidate chains

#### Frequency Expansion

Trips referenced in `frequencies.txt` are never additionally emitted from their stop_times. Each frequencies row expands to departures at `start_time + k·headway_secs` for `k ≥ 0` while the result is strictly before `end_time` (half-open interval). Multiple headway rows for one trip are concatenated in start_time order and must not overlap; overlaps produce a fatal feed issue. `exact_times=0`/blank is an approximation of headway-based service and is recorded as metadata; `exact_times=1` expansion is exact.

#### Multi-Day `all` Mode

Every generated departure carries a unique id of the form:

- schedule trips: `feedId:tripId:YYYYMMDD`
- frequency-expanded trips: `feedId:tripId:YYYYMMDD:HHMMSS` (originating row start time plus expansion index folded into the time component)

Departure ids are unique within a transit line. `all` produces one schedule whose departures are sorted by departure time after aggregation; consumers needing per-day separation can group by the date embedded in the id. API docs must state that one-day MATSim simulations normally use a selected sample day, and that multi-day output changes departure counts by design.

### Transit Schedule Production

GTFS routes correspond to MATSim transit lines. Trips under a route are grouped into MATSim transit routes by:

- GTFS route id
- direction id, with empty normalized to the same value as `0`
- the ordered list of stop ids with duplicates preserved (loop/shuttle trips service a stop more than once)
- the exact per-second arrival/departure offset vector derived per the departure-construction rules; an optional configured tolerance quantizes vectors before comparison (default 0)
- feeds may not merge groups across feeds

`shape_id` and `trip_headsign` are trip metadata, not grouping keys; v1 does not route by shape (recorded limitation). Departures with the same grouped profile share one `TransitRoute`.

Stops are transformed from WGS84 lon/lat to the network CRS before creating `TransitStopFacility` objects. The mapping step requires schedule and network coordinates to be in the same projected CRS whose units are meters. The build result carries a canonical CRS identifier; the mapper rejects schedule/network CRS mismatch with a typed exception rather than producing spatially meaningless output.

### Mode Mapping

All GTFS route types 0 through 12 are supported in v1:

- 0 tram/light rail
- 1 subway/metro
- 2 rail
- 3 bus
- 4 ferry
- 5 cable tram
- 6 aerial gondola/suspended cable car
- 7 funicular
- 11 trolleybus
- 12 monorail

Types 100+ are extended types, accepted and mapped through config. Unknown or unmapped route types generate warnings and default to `pt` unless a config override exists.

`TransitMappingConfig` includes a schedule-mode to network-mode assignment map used for candidate eligibility only. One output network mode is selected per transit route (normally `bus`, `tram`, or `rail`); every selected, intermediate, stop, and artificial link in the finished route carries that output mode. Network mode `car` is a fallback eligibility mode whose used links are augmented with the route's output mode, not an interchangeable output mode. The finished route is validated against that single mode end to end.

### Stop-Link Candidate Scoring

Candidates are selected from mode-compatible links near each GTFS stop using the Phase 1 spatial index. Mode compatibility includes the resolved OSM access profile: a car-mode link whose source way forbids buses is not bus-compatible (see Access Resolution).

Candidate score combines:

- point-to-link distance (full polyline distance, sidecar geometry)
- mode compatibility
- OSM stop hint proximity, with stop-area association outranking pure proximity
- same name/ref/operator/network similarity where available
- side-of-road/platform hints where available
- lane hints such as bus/psv lane availability
- penalty for inaccessible direction
- penalty for implausible turn at adjacent routing node (soft evidence only; turn-lane strings never hard-reject)

Config parameters:

- `nLinkThreshold`
- `candidateDistanceMultiplier`
- `maxLinkCandidateDistanceMeters`
- `routingWithCandidateDistance`
- stop-hint score weights
- lane-hint score weights
- optional per-mode overrides

Weights are validated at config time: negative weights and all-zero weight vectors are rejected.

If no candidate is found, an artificial loop link is created at the stop coordinate and becomes the stop's only candidate. If `maxLinkCandidateDistanceMeters == 0`, all stops use artificial loop links, allowing a fully separate PT network to be generated.

### Route Mapping Algorithm

For each transit route:

1. Select link candidates for every stop, capped by a configured per-stop maximum.
2. Build a pseudo graph whose nodes are link candidates.
3. Compute least-cost paths between candidate pairs for adjacent stops using one bounded multi-target search per upstream candidate (or an equivalent batched computation), with cache keys that include network version, mode set, cost model, and restriction state.
4. Add pseudo edges weighted by real path cost plus candidate penalties. Unreachable and over-threshold transitions stay virtual; no connector is materialized during search.
5. Penalize using the same candidate for adjacent stops.
6. Run Dijkstra on the pseudo graph to select one candidate per stop.
7. Materialize artificial links only for transitions on the selected path that have no valid real path or whose path cost exceeds the configured threshold.
8. Create child stop facilities for each parent stop/link combination.
9. Build each `TransitRoute` network route from the selected candidate paths.
10. Run cleanup and plausibility checks.

Route assembly invariants: path cost and sequence are `upstream candidate + connector/intermediate links + downstream candidate`; every referenced stop link appears in the route in order; consecutive route links are topologically continuous except for reported artificial links; when adjacent stops select the same candidate link, the shared link appears once. Routing caps (max candidates, max visited states, max path cost) prevent pathological searches; exhausted caps produce warnings and artificial links rather than unbounded computation.

### Turn-Restricted Routing

Routing honors MATSim-compatible `disallowedNextLinks` by using an expanded routing state that tracks enough previous link history to reject completing a disallowed sequence for the active mode.

The router state is conceptually:

```text
current node + recent incoming link sequence suffix
```

Partial traversal of a restricted sequence is allowed. Completing the full sequence is forbidden.

### Artificial Links

Artificial links are created in two situations:

- no real network path exists between adjacent chosen candidate links
- the least-cost path exceeds the configured threshold

Artificial links connect the `toNode` of the upstream candidate link to the `fromNode` of the downstream candidate link. Loop links for unmapped stops connect a new stop node to itself.

Artificial links carry positive configured defaults for every required field — length (minimum 1.0 m even for loops), freespeed, capacity, permlanes — plus the route's output mode and the `pt_` id prefix. Artificial ids are derived from route/stop transition tuples (never bare sequence numbers) and are collision-checked.

Created artificial links are recorded in `TransitMappingResult` and exposed to CityModeler as warnings, not hidden. Artificial links created for earlier routes are excluded from later candidate selection and routing unless config allows them, so results are independent of route processing order.

### Child Stop Facilities

One parent stop can map to different links for different routes or directions. The mapper creates child stop facilities per parent stop/link combination.

Default id convention:

- `parentStopId.link:linkId`

The convention is applied only after a collision check against existing prefixed parent ids; a GTFS parent id that already contains the convention's delimiter shape is escaped by doubling the `.link:` separator. Unmapped stops keep their original prefixed facility id with `linkRefId` pointing at their artificial loop link; only mapped stops gain child facilities, and the parent facility remains in the schedule.

Each child stop keeps metadata:

- parent stop id
- original GTFS stop id
- feed id
- agency id, when known
- selected OSM stop hint id, when used
- selected OSM source way id(s)

Metadata keys are fixed (`gtfs:parentStopId`, `gtfs:feedId`, `gtfs:agencyId`, `osm:stopHintId`, `osm:wayIds`) and are part of the XML round-trip contract. Transit route profiles reference child stop facilities after mapping.

### Vehicles

The GTFS builder creates `VehicleDefinitions` in addition to `TransitSchedule`, including `VehicleType` definitions with seating and standing capacity keyed by resulting transit mode. Capacities are conservative defaults and configurable.

Each departure gets its own vehicle: the vehicle id is derived from the departure id, globally unique across all lines and feeds. One vehicle per departure is deliberate; GTFS `block_id` vehicle reuse is out of scope for v1 and recorded as metadata. Vehicle type references resolve: every vehicle references a declared type, and every departure's vehicle exists — violations are fatal build issues.

### Phase 2 Result

`GtfsTransitBuildResult`:

- `TransitSchedule schedule`
- `VehicleDefinitions vehicles` including vehicle types
- line/feed metadata
- selected service date(s) and per-feed service provenance
- warnings

`TransitMappingResult`:

- mapped `TransitSchedule` (new instance; inputs unmodified)
- modified `Network` (new instance; inputs unmodified)
- rebuilt or invalidated-and-replaced `NetworkQueryIndex` covering artificial links
- created artificial nodes/links
- child stop mapping table
- route mapping summaries
- stop candidate diagnostics
- plausibility warnings
- fatal issues, if mapping could not complete

Mapping operates against an immutable routing snapshot of the built network (network plus indexes). Artificial nodes/links accumulate in an overlay during route processing; the final network and a fresh index are materialized once after all routes are mapped. This keeps results independent of route processing order and prevents stale-index queries.

### Phase 0: Model And IO Repairs (Prerequisite)

The existing model layer cannot represent several promised outputs. These repairs are a separate work package executed before Phase 1F/2B, on the existing classes, without MATSim dependencies:

1. `TransitRoute` network route support: ordered link-sequence storage plus `<route><link refId="..."/></route>` in `TransitScheduleXmlWriter`/`TransitScheduleXmlReader`, with continuity validation and MATSim wire-format corrections (`linkRefId` attribute, child-element `transportMode`).
2. `VehicleType` model and `VehicleDefinitions` type collection: seating/standing capacity, dimensions; `VehiclesXmlWriter`/`VehiclesXmlReader` emit and parse `vehicleType` elements; referential validation from vehicle to type and departure to vehicle.
3. `Scenario` gains a `VehicleDefinitions` field; `ScenarioXmlReader` loads a `vehicles` module; a bundle-save path writes network + schedule + vehicles together.
4. Duplicate-key guards: `TransitSchedule.addStopFacility`/`addTransitLine`, `TransitLine.addRoute`, `TransitRoute.addDeparture` throw on duplicate ids instead of silent `put`.
5. `Link` preserves empty `allowedModes` through construction and round-trip instead of defaulting to `car`; the network writer always emits `modes`.
6. `TransitStopFacility` gains a `parentId` field serialized in XML, so child-stop metadata has a typed home.
7. `XmlSupport` attribute codec extension: typeable support for `DisallowedNextLinks` in `classHint`/`convert` (both directions) per the wire contract, with a pluggable converter extension point.
8. `NetworkValidator` options: downgradable self-loop warnings for `pt_`-prefixed artificial links, and optional mode-coverage/length-ceiling checks the plausibility checker can reuse instead of re-implementing.
9. Deterministic ID codec utility: canonical typed-tuple id composition with delimiter escaping, used by OSM link ids, child-stop ids, artificial ids, and prefixed GTFS ids.
10. Memory-bounded writers: streaming StAX output paths for network and transit schedule (existing DOM writers remain for small artifacts).

### Memory Strategy

For city/regional extracts the import is memory-bounded by design:

- OSM parsing uses multi-pass streaming StAX: classification decisions and needed coordinates are captured per pass; the full source record set is not all resident simultaneously (following the existing two-pass facility parser pattern)
- source records can be released once the network build and hint extraction complete; `OsmImportResult` documents its lifecycle
- sidecar stores use primitive-typed or ordinal-based ids where practical
- large network/schedule/vehicle outputs use the streaming writers from Phase 0
- acceptance includes representative peak-heap targets, not only functional correctness

## Testing Strategy

All tests must use synthetic fixtures created for this repository or permissively licensed public sample feeds with documented provenance. Each test task must be executable in isolation (its fixtures created in the same task).

Phase 1 tests:

- OSM XML parsing for nodes, ways, relations, and tags, plus `.osm.gz` input giving identical results to plain input
- highway/railway/ferry rule conversion, including ferry relations and closed-way area rejection
- access hierarchy resolution (parent/child overrides, directional access, conditional-tag conservative outcomes)
- oneway parsing: `yes`, `-1`, `no`, implied roundabout/motorway oneway, `oneway:bus=no` contraflow
- maxspeed parsing, `mph`, `none`/`walk` mapping, unknown-symbol warnings
- lane parsing: directional lanes, derivation from total, `lanes:both_ways`, odd split, conflict warnings, bus/psv prefix and suffix schemes, exclusivity via `access:lanes`
- turn-lane strings parsed as soft evidence (never prohibitions)
- stop hint extraction from nodes, ways (line and area geometry), and relations, including stop-area membership
- barrier passability per mode, including bus traps
- routing-node simplification versus geometry sidecar preservation: at least one fixture with a shape-only interior node; polyline length equals summed segment lengths; `ROUTING_NODES_ONLY` stores two-point polylines; materialized mode increases node/link counts
- turn restriction conversion: prohibitive, mandatory (`only_*` alternative expansion), `except`, mode-suffixed, via-way chains, unresolved warnings; negative assertions that unrelated movements are not restricted
- disallowedNextLinks XML round trip with exact class-hint string assertion (unconditional, not deferred)
- spatial index: nearest-link, nearest-stop with mode filter, equidistant tie-break by id, empty-radius behavior
- cleaner: quarantine of small components, transit-relevant retention under `keepWaysWithTransit=true`, drop-with-issues only when configured off, sidecar compaction to surviving ids
- provenance and ODbL attribution metadata survive network XML round trip with exact values
- no `org.matsim` token in main sources and pom.xml except the single allowlisted MATSim-compatible class-hint constant

Phase 2 tests:

- GTFS CSV parsing, including quotes, missing optional columns, missing `route_type` tolerance
- service materialization: calendar rules, calendar_dates exceptions (added/removed), calendar_dates-only feeds, neither-file failure, `assumeAlwaysActive` warning
- service-day selection: `dayWithMostTrips` counts expanded departures, deterministic tie-breaks
- departure construction: anchored offsets, interpolation of empty times, >24:00 times, monotonicity failures, `pickup_type` stops retained
- frequency expansion: half-open interval, no double emission for frequency trips, overlapping rows rejected, `exact_times` handling
- unique departure ids across service days under `all`; departures sorted per route after aggregation
- route grouping: duplicate-stop sequences (loops), `direction_id` empty vs `0` equivalence, offset-vector identity, no cross-feed merging
- location_type filtering: stations/entrances excluded from facilities, platform fallback to station coordinate with warning
- GTFS Flex rows skipped with aggregate warnings
- CRS transformation smoke tests and CRS-mismatch rejection
- feed id derivation sanitization and collision handling
- mode mapping and unknown route type warnings
- link candidate scoring with OSM stop hints, stop-area association outranking proximity, lane hints, weight-config validation
- artificial loop link creation, zero-candidate-distance mode, artificial-link field validity (positive length/capacity/freespeed/lanes)
- pseudo graph global candidate selection where nearest-link greedy mapping is wrong
- turn-restricted routing with single-link and multi-link disallowed sequences
- child stop facility creation for shared parent stops, id-collision escaping, parent facility retained
- mapped route continuity: stop links present in order, single output mode on all links, gaps exactly equal to reported artificial links
- mapped route cleanup behavior and plausibility warnings
- XML round trip of generated schedule, network (with route sequences and child-stop parent ids), and vehicles (with types)
- every departure's vehicle resolves in `VehicleDefinitions`
- byte-identical output under shuffled input record order (determinism)
- guard tests: no `org.matsim` token outside the allowlisted class-hint constant; pom.xml contains no `org.matsim` artifacts

## Documentation

Add public docs before release:

- OSM import guide
- OSM/ODbL provenance, attribution, and access-offer note
- GTFS import and transit mapping guide
- CityModeler integration guide
- independent-implementation/provenance note for Pt2MATSim-equivalent functionality
- dependency/license notes
- configuration examples

## Design Decisions

- v1 starts with OSM XML input; `.osm.gz` supported via existing gzip IO; PBF is a later additive feature unless an acceptable parser dependency is selected during implementation planning.
- v1 preserves raw OSM tags in sidecar metadata by default, but not necessarily in every MATSim XML link attribute. Access keys needed by the resolver are always parsed.
- v1 default geometry mode is `PRESERVE_AS_LINK_GEOMETRY`, not `MATERIALIZE_GEOMETRY_NODES`.
- v1 uses a dependency-free uniform grid spatial index with per-segment insertion before considering heavier spatial libraries.
- v1 treats GTFS as schedule authority and OSM public-transport data as mapping hints, with stop-area relations outranking proximity.
- v1 keeps route mapping single-threaded until reports and artificial-link generation are deterministic and tested.
- v1 fixes a 3600-second capacity period and documents it until writer support for `capperiod` lands.
- Conditional OSM tags (access, maxspeed, restrictions) are conservatively excluded with warnings rather than approximated.
- GTFS `all` mode is retained with mandatory unique multi-day departure ids, not removed, because CityModeler requires full-departure inspection.

## Acceptance Criteria

Each criterion names its verifying test.

- `mvn clean verify -B` passes
- `NoMatsimReferencesTest`: no `org.matsim` token in main sources except the single allowlisted class-hint constant; no `org.matsim` groupId/artifactId in `pom.xml` (no Pt2MATSim, MATSim, or MATSim contrib dependencies)
- `OsmProvenanceXmlRoundTripTest`: OSM-derived network carries provenance/license metadata with exact values after round trip
- Phase 1 tests demonstrate preservation of configured raw tags, lane hints, stop hints (node/way/relation), relation hints, and link geometry sidecars
- `OsmNetworkCleanerTest.cleanedNetworkIsSingleComponentWithRequiredModes`: exactly one weakly connected component; every link carries a required mode; expected retained node count
- `NetworkQueryIndexTest`: bounded nearest-link and nearest-stop queries with mode filtering, sorted results, id tie-breaks, empty-radius behavior
- `TransitScheduleMatSimFormatTest`: writer output matches MATSim-documented wire format exactly (`linkRefId`, child `transportMode`, `<route>` link sequences)
- `GtfsScheduleRoundTripTest`: generated schedule round-trips through `TransitScheduleXmlWriter`/reader including route profiles and departures
- `MappedChildStopsTest`: mapped schedule has child stop facilities with `linkId`s, `parentId` metadata, and the parent facility retained
- `MappedRouteContinuityTest`: mapped transit routes include network link sequences that are topologically continuous except for reported artificial links, with one output mode
- `ArtificialLinkTest`: artificial links are created and reported when no valid path exists, carry positive metrics, and are order-independent
- `TurnRestrictionRoutingTest`: turn restrictions honored in network routing and transit mapping, including `only_*` and via-way cases
- `VehicleDefinitionsTest`: vehicle types with capacities are generated; every departure's vehicle id resolves
- `DeterministicOutputTest`: byte-identical network/schedule/vehicle XML under shuffled input order
- `PublicApiSurfaceTest`: two-phase public API references only library and JDK types; no GPL dependencies (with pom scan)
