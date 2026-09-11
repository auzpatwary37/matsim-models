# Signal-Ready Network Specification

Date: 2026-09-10

## Purpose

`matsim-models` must be able to produce a MATSim-compatible network that can support future conversion of MATSim signals contribution input/output. The primary goal is to simplify geometry while preserving lane information, turn restrictions, and signal-relevant movement semantics.

The network must distinguish real intersections from geometry-only shape points, preserve the information needed to attach signals to incoming links and turning movements, and retain enough lane information for future lane-aware and ring-barrier signal controllers.

This specification describes the required network-side outcomes. It intentionally does not prescribe algorithms, source-code structure, class names, or implementation techniques.

## Scope

The scope is the OSM-derived network and its metadata as consumed by later signal IO conversion.

The signal-ready network must support:

- geometry simplification without loss of lane, turn, signal, transit, or movement meaning
- fixed-time signal system export/import
- signal groups attached to approaches or movements
- movement-specific signal control using from-link to to-link relationships
- lane-aware signal definitions when lane information is available
- future ring-barrier controller data preparation
- coexistence with transit mapping and turn restrictions

The scope does not include:

- running MATSim signal controllers
- implementing QSim runtime behavior
- implementing a ring-barrier controller
- deriving optimized signal timings from traffic demand
- copying or adapting MATSim, Pt2MATSim, or GPL implementation code

## Clean-Room Boundary

The implementation must be independently authored. Public MATSim documentation, public XML schema behavior, OSM tagging documentation, and GTFS/OSM standards may be used only to identify functional requirements and interoperable file semantics.

The specification must not be implemented by copying MATSim or Pt2MATSim code, comments, fixtures, examples, internal helper names, or tests.

## Core Simplification Principle

The network may simplify geometry, but it must not simplify away meaning.

Geometry-only detail may be collapsed into longer links. Semantic detail must remain represented, queryable, and traceable after simplification.

Semantic detail includes:

- lane count
- lane direction
- turn-lane indications
- lane-to-movement eligibility
- dedicated lane status
- allowed modes
- turn restrictions
- signalized-node identity
- legal from-link to to-link movement structure
- transit stop and transit route references
- OSM provenance needed to explain the resulting network

If a geometry simplification would make any lane, turn restriction, or controlled movement ambiguous, that simplification must not be applied at that location.

## Expected Simplification Outcome

The expected output is a semantically simplified MATSim network.

The network should not contain ordinary routing nodes whose only purpose was to preserve the visual curvature of an OSM way. Those points should instead be represented as geometry associated with the resulting link.

The network should contain nodes only where the model needs a decision point, a control point, a semantic change, or a stable attachment point.

Expected node-level outcome:

- A straight or curved road made from many OSM shape nodes becomes one or more longer network links between meaningful nodes.
- Intermediate OSM nodes that only describe curvature are not ordinary network nodes.
- A real intersection remains a network node.
- A signalized OSM node remains a network node or remains represented as part of a signalized junction cluster.
- A node with a turn restriction relation remains represented as a movement decision point.
- A node where lane count, lane direction, turn-lane meaning, access, speed, one-way direction, or mode permission changes remains represented as a network node.
- A node used to attach a transit stop remains represented when stop preservation is enabled.

Expected link-level outcome:

- A simplified link may span several original OSM node-to-node segments.
- A simplified link must retain the full original geometry as a shape/polyline representation.
- A simplified link must have stable deterministic IDs.
- A simplified link must retain provenance back to the OSM way and source segment range it represents.
- A simplified link must not cross a real intersection, signalized junction, lane-semantic boundary, access boundary, or turn-restriction decision point.
- A simplified link must not claim uniform lane or access properties if those properties changed along the original OSM span.

Expected signal-readiness outcome:

- Every signalized junction can enumerate its incoming links.
- Every signalized junction can enumerate its outgoing links.
- Every legal controlled movement can be represented as `fromLinkId -> toLinkId`.
- Every prohibited movement remains prohibited after simplification.
- Every movement relevant to signal IO references links that exist in the simplified network.

## OSM Inputs To Preserve Or Interpret

The following OSM information is relevant to the expected simplification result. These tags and relations define whether a point or segment is geometry-only or semantically meaningful.

Node-level OSM inputs:

- `highway=traffic_signals`: marks a signalized control point.
- `traffic_signals=*`: describes signal type, direction, or control detail when present.
- `highway=stop`, `highway=give_way`, `crossing=*`: may indicate control or crossing semantics relevant to a junction.
- public transport stop tags such as `highway=bus_stop`, `public_transport=platform`, `public_transport=stop_position`, `railway=tram_stop`, `railway=station`, `railway=halt`: may require preservation for transit attachment.
- barrier or access-control tags such as `barrier=*`, `bollard=*`, `access=*`: may create a semantic boundary.

Way-level OSM inputs:

- `highway=*`, `railway=*`, `route=ferry`: determine network inclusion and modal class.
- `oneway=*`, `oneway:bus=*`, `oneway:psv=*`, `oneway:bicycle=*`: determine directionality by mode.
- `access=*`, `motor_vehicle=*`, `vehicle=*`, `bus=*`, `psv=*`, `taxi=*`, `bicycle=*`, `foot=*`: determine mode access.
- `lanes=*`, `lanes:forward=*`, `lanes:backward=*`: determine lane counts and directional lane allocation.
- `turn:lanes=*`, `turn:lanes:forward=*`, `turn:lanes:backward=*`: determine lane-to-movement meaning.
- `bus:lanes=*`, `psv:lanes=*`, `taxi:lanes=*`, `bicycle:lanes=*`: determine dedicated or restricted lane usage.
- `maxspeed=*`, `maxspeed:forward=*`, `maxspeed:backward=*`: determine speed semantics and boundaries.
- `junction=*`: identifies special junction forms, including roundabouts and link-style junctions.
- `name=*`, `ref=*`, `operator=*`: support provenance, matching, and diagnostics.

Relation-level OSM inputs:

- `type=restriction`: defines prohibited or mandatory turns.
- restriction members `from`, `via`, and `to`: identify the movement decision point and affected OSM ways/nodes.
- restriction exception tags such as `except=*`: define mode-specific exemptions.

When any of these inputs changes along an OSM way, the output network must either preserve a node at that change point or explicitly preserve the limited applicability of the changed property.

## Node-By-Node Expected Outcome

Each OSM node referenced by an included OSM way must fall into one of these output categories.

### Preserved Network Node

An OSM node is expected to become or remain a network node when it has routing, lane, signal, access, transit, or provenance significance.

Examples:

- The node is the first or last node of an included way.
- The node is shared by multiple included ways.
- The node has incoming and outgoing choices after considering directionality.
- The node is `highway=traffic_signals` or has `traffic_signals=*`.
- The node is a `via` member of a turn restriction relation.
- The node is needed to represent a mode-specific access change.
- The node is needed to represent a lane-count or turn-lane change.
- The node is needed to attach a transit stop or stop position.
- The node is explicitly configured to be retained.

Expected final state:

- The node has a stable network node ID.
- The node has projected coordinates.
- The node participates in valid incoming/outgoing link topology.
- Signal/lane/turn/transit/provenance attributes remain queryable when applicable.

### Geometry-Only Shape Point

An OSM node is expected not to become an ordinary network node when it only describes the geometry of a way.

Expected final state:

- The point is retained in the geometry/polyline of the simplified link.
- It is not treated as an intersection.
- It is not considered a candidate signal system.
- It does not create an artificial routing decision.

### Signalized Junction Cluster Member

Some OSM signalized intersections may be represented by multiple nearby OSM nodes rather than one clean node.

Expected final state:

- The cluster remains represented as one signal-relevant junction identity when appropriate.
- Each controlled incoming link and outgoing movement remains resolvable.
- The cluster is not flattened in a way that loses which approaches and movements are controlled.

## Link-By-Link Expected Outcome

Each output link must represent one semantically uniform directed travel segment.

For every output link, the following must be true:

- Its from-node and to-node are preserved network nodes.
- Its directionality is correct for the controlled mode set.
- Its allowed modes are correct for the full link span.
- Its lane count is correct for the full link span, or partial applicability is explicit.
- Its dedicated lane information is correct for the full link span, or partial applicability is explicit.
- Its turn-lane meaning at the downstream end remains attached to the downstream approach.
- Its speed/capacity/lane assumptions are consistent with the represented span.
- Its original geometry is preserved sufficiently for rendering and distance-based matching.
- Its provenance identifies the source OSM way and the original node range or segment range represented.

An output link must end before a point where any of the following changes:

- allowed modes
- directionality
- number of lanes
- directional lane allocation
- dedicated lane designation
- turn-lane meaning
- speed class or explicit maxspeed
- signal control relevance
- turn restriction decision point
- transit stop attachment requirement
- true intersection topology

## Example Outcomes

### Curved Road With No Semantic Changes

Input shape:

```text
A -- s1 -- s2 -- s3 -- B
```

Where `s1`, `s2`, and `s3` are only geometry points.

Expected output:

```text
A ===== B
```

Expected properties:

- one directed link per legal direction between `A` and `B`
- geometry contains `A, s1, s2, s3, B`
- `s1`, `s2`, `s3` are not routing nodes
- no signal system is inferred at `s1`, `s2`, or `s3`

### Road With Lane Change Midway

Input shape:

```text
A -- s1 -- L -- s2 -- B
```

Where lane count or lane designation changes at `L`.

Expected output:

```text
A ===== L ===== B
```

Expected properties:

- `L` is preserved as a network node or equivalent semantic boundary
- the upstream and downstream links each have truthful lane metadata
- lane information is not smeared across `A -> B`

### Signalized Intersection

Input shape:

```text
N_in -- s1 -- J -- s2 -- N_out
            |
          E/W road
```

Where `J` is signalized.

Expected output:

```text
N_in ===== J ===== N_out
            |
          E/W links
```

Expected properties:

- `J` remains signal-relevant
- incoming links into `J` are stable signal attachment points
- outgoing links from `J` are stable turning-move targets
- controlled movements can be expressed as `incomingLink -> outgoingLink`

### Turn Restriction At A Junction

Input condition:

```text
from way -> via node -> to way
restriction: no_left_turn
```

Expected output:

- the via decision point remains represented
- the prohibited movement remains expressible after simplification
- the from-link and to-link referenced by the movement exist in the simplified network
- no simplification makes the prohibited turn appear legal

## Desired Network Semantics

The generated network must separate three concepts that are currently easy to confuse:

- geometry shape points: points used only to represent road curvature
- routing nodes: points where path choice, topology, or travel behavior can change
- signalized junctions: routing nodes or node clusters where signal control may apply

A signal-ready network must avoid both over-detail and over-simplification.

Over-detail occurs when geometry-only nodes remain as ordinary routing nodes and are incorrectly treated as intersections.

Over-simplification occurs when real junctions, turn movements, lane split/merge points, signal stop lines, transit stop access points, or restriction-bearing nodes are removed or merged away.

## Node Preservation Requirements

A network node must be preserved as a routing node if it carries any traffic, transit, signal, or lane meaning.

The network must preserve nodes representing:

- true intersections or branch points
- signalized nodes
- traffic-signal stop-line nodes, when identifiable
- nodes involved in turn restrictions
- nodes where legal movement choices differ by mode
- nodes where allowed modes change
- nodes where lane count changes
- nodes where turn-lane information begins, ends, or changes
- nodes where dedicated transit, bus, bicycle, taxi, or psv lane information changes
- nodes associated with transit stops, when stop preservation is enabled
- user-explicitly preserved OSM nodes
- network boundaries and dead-ends
- nodes required to keep link provenance and route references unambiguous

A node may be treated as geometry-only only when removing it would not change topology, mode access, movement legality, lane meaning, signal meaning, transit stop attachment, or provenance-relevant references.

## Lane And Turn Preservation Invariants

The following invariants define correctness for signal-ready simplification:

- Every turn restriction present before simplification must still refer to a valid movement after simplification.
- A prohibited movement must not become legal because a node or link was merged.
- A legal movement must not become prohibited because a node or link was merged.
- Lane information must remain attached to the correct approach direction after simplification.
- Turn-lane information must remain attached to the movement or movement set it describes.
- A lane-bearing segment must not be merged with another segment if the merge would blur where lane meaning starts or ends.
- A signalized node must remain identifiable as a signalized junction or part of a signalized junction cluster.
- Incoming links used for signal control must remain stable enough to be referenced by signal IO.
- Outgoing links used as turning-move targets must remain stable enough to be referenced by signal IO.
- Provenance must make clear whether lane and turn data came directly from OSM tags or from network-derived inference.

These invariants are more important than reducing node/link count.

## Link Merging Requirements

When geometry-only nodes are not retained as routing nodes, the resulting link must remain behaviorally truthful.

Merged links must preserve or accurately represent:

- total geometric length
- original geometry as a polyline or equivalent sidecar representation
- from-node and to-node topology
- allowed modes
- capacity-relevant lane information
- freespeed or travel-time-relevant speed information
- source OSM way provenance
- directionality
- transit compatibility
- lane and turn-lane applicability over the merged span

Links must not be merged across a point where the legal, lane, signal, transit, or routing meaning changes.

When links are merged, any lane or restriction metadata associated with the original links must remain representable without pretending it applies uniformly where it does not. If metadata applies only to part of a merged link, the output must either preserve that sub-span as a separate semantic segment or expose the partial applicability explicitly.

## Signalized Junction Requirements

The network must expose enough information for later signal IO to create one signal system per signalized junction or signalized junction cluster.

For each signalized junction, the network-side data must identify:

- the junction identity
- the controlling node or nodes
- incoming approach links
- outgoing downstream links
- legal turning movements from each incoming link to outgoing links
- prohibited movements, including turn restrictions
- signal-relevant lanes when available
- whether control applies to the whole incoming link, selected lanes, selected turning movements, or a combination
- source confidence and provenance

Signalized junctions must not be inferred solely from node degree. A node with multiple links is not necessarily signalized, and a signalized intersection may require grouping multiple nearby OSM nodes.

## Movement Requirements

The fundamental signal-ready movement is:

```text
fromLinkId -> toLinkId, optionally constrained by laneIds and mode
```

For every signalized junction, the network must be able to enumerate all legal controlled movements.

Each movement must have:

- incoming link ID
- outgoing link ID
- controlled modes, when mode-specific control is known
- turn type when inferable: left, through, right, u-turn, merge, split, or unknown
- lane references when lane-level information is available
- restriction status: legal, prohibited, unknown, or uncontrolled
- conflict-relevant geometry sufficient for later grouping

Unknown movement details must be represented explicitly rather than silently treated as known.

## Lane Requirements

The network must preserve lane information at a level sufficient for signal IO and future ring-barrier control.

For each approach link, the network-side data should be able to represent:

- total lane count
- lane count by direction when available
- lane order when available
- turn-lane indications when available
- lane-to-movement eligibility when available
- dedicated bus, tram, bicycle, taxi, psv, or transit lanes
- lane restrictions by mode
- lane information confidence and source tags

Lane information must distinguish:

- absent data
- data explicitly saying no dedicated lane exists
- ambiguous or unsupported lane syntax
- partial lane information

Signal IO must not invent precise lane-level signal control when lane data is absent or ambiguous.

## Turn Restriction Requirements

The signal-ready network must treat turn restrictions as part of the legal movement definition.

For signal IO preparation, turn restriction data must support:

- movement-level prohibition: from-link to to-link by mode
- restriction provenance
- mode exceptions
- compatibility with transit route mapping
- preservation of restriction-bearing junctions during simplification

Signal groups must not include movements that are prohibited for the controlled mode.

## Transit Compatibility Requirements

The signal-ready network must remain compatible with transit mapping.

Signal-aware simplification must preserve:

- links referenced by transit stop facilities
- links referenced by mapped transit routes
- artificial transit links, when present
- parent/child stop relationships
- transit-only and mixed-traffic links
- movement legality for transit modes

Signal generation must be able to distinguish public transport movements from general car movements when their allowed modes differ.

## Ring-Barrier Readiness Requirements

Although ring-barrier control is out of scope for this phase, the network must retain information needed by a later ring-barrier controller.

For every candidate signalized junction, the network-side data should be sufficient to derive or store:

- approach identity
- approach ordering around the junction
- movement turn type
- movement conflicts
- protected versus permissive movement status, when known
- lane groups serving each movement
- barrier-relevant opposing or crossing approaches
- phase-group eligibility

If any of this information is unavailable, the network must expose that uncertainty so later controller generation can choose conservative defaults or require user input.

## MATSim Signals IO Compatibility Requirements

The network-side data must support later production and reading of MATSim signals contribution files.

At minimum, signal IO must be able to resolve:

- signal system ID to junction/network node context
- signal ID to link ID
- signal group ID to one or more signals
- signal lane references, when lane-based signals are used
- signal turning-move restrictions as outgoing link IDs
- signal timing plans as independent control data referencing signal group IDs

The network must therefore guarantee stable, deterministic IDs for signal-relevant links, nodes, lanes, movements, and junctions.

## Provenance Requirements

Signal-relevant network data must retain provenance sufficient for review and reproducibility.

For each preserved signal/lane/junction hint, provenance should include:

- source OSM element type
- source OSM element ID
- source tag keys and normalized values used
- confidence level
- derived network IDs affected by the source element
- whether the value is direct source data or inferred metadata

This provenance is required because OSM traffic signal and lane tagging can be incomplete or inconsistent.

## Determinism Requirements

The signal-ready network must be deterministic.

Given the same input extract and configuration, output must be stable for:

- network node IDs
- network link IDs
- signalized junction IDs
- movement IDs
- lane IDs, when emitted
- ordering of incoming approaches
- ordering of outgoing movements
- serialized output

Determinism is required for regression testing, reproducible scenario builds, and stable signal IO references.

## Validation Requirements

The network build result must provide diagnostics for signal readiness.

Diagnostics should identify:

- candidate signalized junctions
- preserved signal-related nodes
- geometry-only nodes removed or retained
- lane hints used for signal readiness
- unsupported or ambiguous lane tags
- turn restrictions affecting signalized movements
- signalized nodes with no usable incoming links
- incoming links with no legal outgoing movements
- movements lacking lane assignment despite lane-aware signal mode
- junctions that require user review before signal generation

Diagnostics must distinguish fatal errors from warnings and informational findings.

## Acceptance Criteria

The feature is complete when the network model can truthfully represent signal-ready topology and metadata without needing MATSim runtime classes.

Acceptance criteria:

- geometry-only nodes are not mistaken for signalized intersections
- signalized nodes are preserved through network simplification
- lane-bearing nodes are preserved when lane meaning would otherwise be lost
- turn-restriction-bearing nodes are preserved
- legal signal movements can be enumerated as from-link to to-link pairs
- movement-level restrictions are represented without ambiguity
- signal-relevant lanes are represented when source data supports them
- ambiguous or missing lane/signal data is surfaced explicitly
- transit stop and transit route references remain valid after simplification
- output IDs are deterministic and stable
- no MATSim or Pt2MATSim runtime dependency is introduced

## Non-Goals

This specification does not require:

- computing optimized traffic signal timings
- running a traffic signal controller
- implementing QSim behavior
- guaranteeing real-world signal phasing from incomplete OSM tags
- generating ring-barrier timing plans
- modeling pedestrian signal phases
- modeling detector-actuated control
- lane-level simulation when lane data is unavailable

## Relationship To Existing Phase Work

Phase 1 already provides the foundation: OSM-derived network topology, lane hints, intersection lane hints, stop hints, geometry sidecars, and turn restrictions.

The missing network-side outcome is a signal-aware simplification and preservation contract: the network must collapse only geometry-only detail while preserving all topology, lane, signal, turn, and transit semantics required by later signal IO.

Phase 2 transit mapping depends on this because mapped transit routes and child stops must remain attached to links that continue to exist after any signal-aware network preparation.

Future signal IO conversion should consume this signal-ready network metadata rather than independently guessing junctions from raw node degree.
