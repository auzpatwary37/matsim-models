# Compatibility

This document describes what `matsim-models` supports and the limitations of its MATSim compatibility.

## What `matsim-models` Is

`matsim-models` is a pure Java 17 library for reading and writing MATSim XML files. It provides:
- Domain model classes (Network, Population, TransitSchedule, Config, etc.)
- XML readers and writers compatible with MATSim 2025.0 XML format
- Zero runtime dependency on MATSim

It does **not** include:
- MATSim simulation runtime
- MATSim controler or analysis tools
- Network / population editing algorithms
- Any MATSim core library classes

## Architecture

Applications use `matsim-models` for XML I/O while MATSim simulation runs as a separate process:

```
[Your Application]  <--->  [matsim-models]  <--->  XML files  <--->  [MATSim Runtime Process]
```

## Feature Coverage by Domain

### Network

| Feature | Status | Notes |
|---------|--------|-------|
| Read/write network XML | Supported | |
| Nodes with coordinates | Supported | |
| Links with capacity, freespeed, lanes | Supported | |
| Link allowed modes | Supported | Default `"car"` if not specified |
| gzip compressed network files | Supported | Detected by `.gz` extension |
| Network route information | Supported | Only `NetworkRoute` model; custom route types via `UnknownRoute` |
| Lanes | Partial | Read/write basic lane geometry; advanced lane models not tested |

### Population

| Feature | Status | Notes |
|---------|--------|-------|
| Read/write population XML | Supported | |
| Person and plan elements | Supported | |
| Activity with facility, link, coordinates | Supported | |
| Activity start_time, end_time, maximumDuration | Supported | |
| Leg with mode | Supported | |
| NetworkRoute (car) | Supported | |
| TransitPassengerRoute (pt) | Supported | |
| Unknown/custom route types | Supported | Preserved via `UnknownRoute` |
| gzip compressed population files | Supported | |
| Score preservation | Supported | |
| Plan attributes | Supported | |

### Transit Schedule

| Feature | Status | Notes |
|---------|--------|-------|
| Read/write transitSchedule XML | Supported | |
| TransitStopFacility | Supported | |
| TransitLine and TransitRoute | Supported | |
| Departures | Supported | |
| Mixed transit/physical mode links | Partial | Not tested with complex multi-modal networks |

### Config

| Feature | Status | Notes |
|---------|--------|-------|
| Read/write config XML | Supported | |
| ConfigGroup subclasses | Supported | |
| Nested config values | Supported | |
| Comments in config | Not preserved | Comments stripped on read/write |

### Events

| Feature | Status | Notes |
|---------|--------|-------|
| Read/write events XML | Supported | |
| gzip compressed events files | Supported | |
| Streaming large event files | Supported | Via `EventsXmlReader.stream()` |
| Common event types | Supported | Activity, Leg, Link, Person, Vehicle, Transit events |
| Custom/extension event types | Partial | Deserializable as generic `MatsimEvent`; type-specific fields may be missing |

### Vehicles

| Feature | Status | Notes |
|---------|--------|-------|
| Read/write vehicle definitions XML | Supported | |
| VehicleType with parameters | Supported | |
| Id-based vehicle assignments | Supported | |

### Households

| Feature | Status | Notes |
|---------|--------|-------|
| Read/write households XML | Supported | |
| Household members and vehicles | Supported | |

### Lanes

| Feature | Status | Notes |
|---------|--------|-------|
| Read/write lanes XML | Supported | |
| Lane geometry and parameters | Supported | |
| Lanes-to-network mapping | Supported | |

## Limitations

1. **No simulation**: `matsim-models` does not run MATSim simulations. Use a separate MATSim process.
2. **No MATSim API compatibility**: Application code must use `matsim-models` types, not MATSim `org.matsim.*` types.
3. **Unknown route types preserved as opaque data**: Routes with unrecognized types are stored in `UnknownRoute` with their attributes and child elements preserved. They round-trip through XML but are not interpreted as typed routes.
4. **Schema validation optional**: Schema validation is off by default. Enable with `new PopulationXmlReader(true)` etc.
5. **Whitespace and comments lost**: XML whitespace and comments are not preserved through read/write cycles.
6. **No config value coercion**: Config values are stored as strings; no type conversion is applied.

## MATSim Version Notes

- `matsim-models` targets Java 17 and does not depend on MATSim
- XML format compatibility is maintained for MATSim 2025.0 and later
- MATSim 2026.0 requires Java 25 and runs as an external process; `matsim-models` XML output remains compatible