# MATSim GUI Audit-First MVP Design

Date: 2026-04-25
Owner: Ashraf Uz Zaman Patwary
Status: Approved in brainstorming session

## 1) Product Intent

This product is a research-to-industry transition of MATSim tooling into a professional desktop GUI for agency use.

Primary v1 goals:
- Enable visual scenario editing
- Enable end-to-end execution and interpretation
- Make every scenario decision auditable and defensible

North star for v1 is audit confidence. Speed and interpretation quality matter, but are secondary to trustworthy traceability and reproducibility.

## 2) Target User and Pilot Context

Primary pilot context:
- Direct agency pilot (not research-only showcase)

Primary user persona:
- Government planning analysts

Secondary user persona:
- Modeling consultants supporting agency workflows

Pilot proof required:
- Both (a) full edit-to-run workflow and (b) robust auditability in one demonstrable flow

## 3) Chosen Product Approach

Selected approach: Audit-First Vertical Slice.

Why chosen:
- Matches the key selling point (defensible planning process)
- Supports agency procurement narrative (traceability, reproducibility, governance)
- Fits solo part-time capacity by limiting scope while preserving end-to-end value

Approaches intentionally not selected for v1:
- Workflow-first broad editor platform
- Analytics-first demonstrator

## 4) MVP Boundary

In scope:
- Open MATSim scenarios
- Perform constrained, high-value edits
- Run MATSim from the GUI
- View basic interpretation outputs
- Export an auditable package for review/replay

Out of scope for v1:
- Advanced visual analytics suites
- Broad editing across every MATSim domain entity
- Static assignment and calibration extras as core promises
- Multi-user cloud collaboration
- Enterprise plugin ecosystem readiness

Editing scope (v1):
- Core network/facility/transit-stop operations only:
  - add
  - move
  - delete
  - key attribute updates

Interpretation scope (v1):
- Basic but useful metrics only:
  - link-level volume and travel-time deltas
  - stop-level basic counts
  - 3-5 pilot KPIs

## 5) System Architecture (V1)

Architecture style:
- Desktop-first monolith with strict internal module boundaries

Rationale:
- Minimizes operational complexity for solo development
- Preserves future extraction points if scale or team size grows

Core modules:
1. Scenario IO
   - Import/export MATSim files
   - Referential and schema validation
2. Map and Layer Engine
   - Rendering, layer controls, selection
   - Viewport culling and progressive drawing for metro scale
3. Edit Command Engine
   - All edits expressed as typed commands
   - No direct UI state mutation
4. Audit Log Store
   - Append-only event stream
   - Metadata capture (actor, timestamp, rationale, source panel, affected IDs)
5. Simulation Orchestrator
   - MATSim execution control
   - Status/log streaming and artifact management
6. Results Reader
   - Parse selected run outputs
   - Build lightweight indexed summaries for map/KPI views
7. Version and Compare
   - Checkpointing, diffing, replay support

State principle:
- Current scenario state is materialized from base scenario + ordered command stream
- Periodic checkpoints reduce replay overhead

## 6) Data Flow and Audit Package

Primary data flow:
1. Open scenario
   - Load files, validate references, build indexes, progressively render layers
2. Commit edit
   - Create typed command with metadata and rationale
   - Validate -> apply -> append event log -> refresh indexes/render
3. Checkpoint
   - Persist compact scenario snapshots after thresholds/milestones
4. Run simulation
   - Bind immutable scenario hash + config hash + tool version
   - Execute MATSim and capture runtime outputs
5. Interpret results
   - Parse selected outputs into map-friendly aggregates
6. Compare and replay
   - Diff versions/runs and support step replay for review

Audit package contents:
- `manifest.json` (scenario/run identity and provenance)
- `actions.jsonl` (append-only command log with rationale)
- `checkpoints/` (optional compressed snapshots)
- `run/` (config used, logs, selected outputs, summaries)
- `diff-report.json` (structured deltas)
- human-readable report (markdown or PDF export)

Integrity model:
- Hash chaining across events/checkpoints to detect tampering

## 7) Trust and Error-Handling Rules

Non-negotiable trust rules:
- Fail closed on integrity violations
- No silent mutation of model state
- Every accepted change must be auditable
- Every run must be reproducibly bound to immutable inputs

User trust cues:
- Visible audit status indicator:
  - clean
  - warning
  - incomplete

Recovery behavior:
- Crash-safe append-only logging
- Restart from last valid checkpoint plus command tail replay

## 8) Performance and Scale Position

Design commitment:
- Same feature set across small and large scenarios
- Explicitly metro-scale capable in v1

Practical UX target:
- Progressive loading and interaction without long UI freezes
- Not necessarily instant operations, but consistently usable

## 9) Verification and Test Strategy

Given solo part-time capacity, prioritize high-leverage verification:
- Contract tests:
  - import/export integrity
  - command validation
  - replay determinism
  - run reproducibility hashing
- Golden replay tests:
  - fixed command stream must produce identical scenario hash and KPI outputs
- Scale smoke tests:
  - metro-scale open/pan/zoom/select and run handoff
- Minimal E2E automation:
  - open -> edit -> run -> interpret -> export

Release gate:
- No release if determinism/integrity/pilot-flow gates fail

## 10) Pilot Acceptance Criteria

Pilot is accepted when all are true:
1. Analyst completes open -> edit -> run -> interpret -> export with no scripting
2. Reviewer can re-open package and verify scenario/run identity integrity
3. Diff report clearly states what changed and basic KPI deltas versus baseline
4. At least one metro-scale scenario is processed end-to-end in pilot conditions
5. Audit trail answers who changed what, when, why, and resulting impact

## 11) 90-Day Execution Slices

Day 0-30 (Foundation):
- Scenario IO contracts
- Command engine skeleton
- Append-only audit log
- Checkpoint format
- Progressive map rendering baseline

Day 31-60 (Vertical Slice):
- Core edit operations
- Validation and error UX
- MATSim run orchestrator
- Reproducible run binding
- Basic KPI extraction

Day 61-90 (Pilot Hardening):
- Compare and replay UX
- Audit package export/import
- Determinism and integrity gate completion
- Metro-scale stabilization
- Pilot demo script and dataset preparation

Go/no-go rule:
- Do not expand scope if audit replay and reproducible hashing are unstable

## 12) Deferred Items (Explicit)

Accepted deferrals for v1:
- Advanced visual analytics
- Broad editing breadth beyond core operations
- Static-assignment/calibration extras as required pilot scope

These may be pulled into v2 based on pilot feedback and funding trajectory.
