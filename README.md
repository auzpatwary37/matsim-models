# CityModeler MATSim I/O

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![CI](https://github.com/auzpatwary37/matsim-models/actions/workflows/ci.yml/badge.svg)](https://github.com/auzpatwary37/matsim-models/actions/workflows/ci.yml)

> **Independence disclaimer**
>
> MATSim is referenced solely to describe intentional file-format interoperability. This
> project is independent and is not affiliated with, endorsed by, or maintained by the
> MATSim project or its maintainers.

**CityModeler MATSim I/O** (Maven coordinate [`com.citymodeler:matsim-models`](https://github.com/auzpatwary37/matsim-models))
is a pure Java 17 library providing POJOs and XML I/O for MATSim file formats. It offers a
lightweight, standalone way to read, write, and manipulate MATSim XML files without
requiring the MATSim runtime library.

## License & attribution

The **software written in this repository** is licensed under the [Apache License,
Version 2.0](LICENSE).

That license applies to the original code authored here and does **not** automatically
extend to:

- **OpenStreetMap data** or any **OSM-derived/generated databases** (see the
  [OpenStreetMap / ODbL](#openstreetmap-data-odbl) section);
- the **MATSim schema/DTD specification files** vendored in this repository;
- **other third-party fixtures or material**.

See **[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)** for the per-item source, license,
and usage of every bundled third-party item, including the sections whose upstream terms
could not be verified.

## Features

- **MATSim-compatible domain models**: pure Java POJOs for network, population, transit, config, events, vehicles, households, lanes, and facilities
- **XML readers and writers**: reads and writes the MATSim XML wire formats listed in the [compatibility matrix](#compatibility-matrix); unknown route types and attributes are preserved round-trip
- **Precise wire-format interoperability**: selected formats are validated against the published MATSim schema/DTD versions listed in the compatibility matrix
- **Apache 2.0 licensed software**: the original code in this repository is reusable by any project, subject to the exclusions above
- **Streaming support**: efficient processing of large event files
- **Modern Java**: built on Java 17 with records and pattern matching

## Installing

### From GitHub Packages

Add the repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github-matsim-models</id>
        <url>https://maven.pkg.github.com/auzpatwary37/matsim-models</url>
    </repository>
</repositories>
```

Add the dependency (use the latest released version; `1.0.3` shown for reference):

```xml
<dependency>
    <groupId>com.citymodeler</groupId>
    <artifactId>matsim-models</artifactId>
    <version>1.0.3</version>
</dependency>
```

For GitHub Actions CI, configure authentication:

```yaml
- name: Maven Login
  run: |
    mkdir -p ~/.m2
    cat > ~/.m2/settings.xml << EOF
    <settings>
      <servers>
        <server>
          <id>github-matsim-models</id>
          <username>${{ github.actor }}</username>
          <password>${{ secrets.GITHUB_TOKEN }}</password>
        </server>
      </servers>
    </settings>
    EOF
```

### From Source

```bash
git clone https://github.com/auzpatwary37/matsim-models.git
cd matsim-models
mvn clean install
```

## Compatibility matrix

This library targets the MATSim **XML wire formats** below. "Validated against the
published schema/DTD" means an in-repo test oracle (or the opt-in reader) checks the
writer output and/or a hand-authored fixture against the corresponding **published**
MATSim schema/DTD. The published schemas are those served from
`https://matsim.org/files/dtd/`; the vendored copies were adapted from the reference
MATSim build `2027.0-2026w25`.

| MATSim wire format | Published schema/DTD version validated in-repo | How validated |
|--------------------|-------------------------------------------------|---------------|
| `vehicleDefinitions` | `vehicleDefinitions_v2.0.xsd` (with `matsimCommon.xsd`, `vehicleDefinitionsEnumTypes.xsd`) | Opt-in reader `new VehiclesXmlReader(true)` + test oracle `MatsimVehicleSpecValidationTest` |
| `transitSchedule` | `transitSchedule_v2.dtd` | Test oracle `TransitScheduleSpecValidationTest` (writer output + hand-authored fixture) |
| network, population, config, events, facilities, lanes, households, scenario | none (repo-local permissive `xs:anyType` stubs) | Round-trip and well-formedness only |

Notes:

- Compatibility is asserted for **the schema/DTD versions listed above**, not as a blanket
  guarantee across all MATSim releases. Earlier, broader "MATSim 2025.0" claims are
  intentionally withdrawn.
- "None (repo-local permissive stubs)" means those readers follow the documented MATSim
  wire format and preserve unknown content, but are **not** checked against MATSim's
  published XSDs in this repository. Schema validation is off by default everywhere.

## Quick Examples

### NetworkXmlReader

```java
import com.citymodeler.matsim.models.io.*;
import com.citymodeler.matsim.models.network.*;
import java.nio.file.Path;

// Read a network
Network network = new NetworkXmlReader().read(Path.of("network.xml"));

// Access nodes and links
for (Node node : network.getNodes().values()) {
    System.out.println("Node: " + node.getId() + " at " + node.getCoord());
}

for (Link link : network.getLinks().values()) {
    System.out.println("Link: " + link.getId() +
                       " from " + link.getFromNode() +
                       " to " + link.getToNode());
}

// Write back
new NetworkXmlWriter().write(network, Path.of("output-network.xml"));
```

### PopulationXmlReader

```java
import com.citymodeler.matsim.models.io.PopulationXmlReader;
import com.citymodeler.matsim.models.population.*;
import java.nio.file.Path;

Population population = new PopulationXmlReader().read(Path.of("population.xml"));

for (Person person : population.getPersons().values()) {
    System.out.println("Person: " + person.getId());
    for (Plan plan : person.getPlans()) {
        for (PlanElement element : plan.getPlanElements()) {
            if (element instanceof Activity activity) {
                System.out.println("  Activity: " + activity.getType() +
                                   " at " + activity.getCoord());
            } else if (element instanceof Leg leg) {
                System.out.println("  Leg: " + leg.getMode());
            }
        }
    }
}
```

### TransitScheduleXmlReader

```java
import com.citymodeler.matsim.models.io.TransitScheduleXmlReader;
import com.citymodeler.matsim.models.transit.*;
import java.nio.file.Path;

TransitSchedule schedule = new TransitScheduleXmlReader().read(Path.of("transitSchedule.xml"));

for (TransitLine line : schedule.getTransitLines().values()) {
    System.out.println("Line: " + line.getId());
    for (TransitRoute route : line.getRoutes().values()) {
        System.out.println("  Route: " + route.getId());
        for (Departure departure : route.getDepartures().values()) {
            System.out.println("    Departure at: " + departure.getDepartureTime());
        }
    }
}
```

### ConfigUtils

```java
import com.citymodeler.matsim.models.config.*;
import java.nio.file.Path;

Config config = ConfigUtils.loadConfig(Path.of("config.xml"));

// Access config groups
for (ConfigGroup group : config.getConfigGroups()) {
    System.out.println("Group: " + group.getName());
}

// Write config
ConfigUtils.writeConfig(config, Path.of("output-config.xml"));
```

### ScenarioUtils

```java
import com.citymodeler.matsim.models.scenario.*;
import java.nio.file.Path;

// Load a complete scenario
Scenario scenario = ScenarioUtils.loadScenario(Path.of("config.xml"));

// Access all components
Network network = scenario.getNetwork();
Population population = scenario.getPopulation();
TransitSchedule transit = scenario.getTransitSchedule();
```

### EventsXmlReader

```java
import com.citymodeler.matsim.models.io.EventsXmlReader;
import com.citymodeler.matsim.models.events.*;
import java.nio.file.Path;

List<MatsimEvent> events = new EventsXmlReader().read(Path.of("events.xml"));

for (MatsimEvent event : events) {
    System.out.println("Event at t=" + event.getTime() + ": " + event.getEventType());
    if (event instanceof LinkEnterEvent e) {
        System.out.println("  Person " + e.getPersonId() + " enters link " + e.getLinkId());
    }
}
```

## Programmatic Construction

When constructing models in code, call `postProcess()` after all nodes, links, stops, routes, and persons have been added. Readers call this automatically.

```java
Network network = new Network();
network.addNode(new Node(Id.create("n1", Node.class), new Coord(0, 0)));
network.addNode(new Node(Id.create("n2", Node.class), new Coord(100, 0)));
network.addLink(new Link(Id.create("l1", Link.class),
                         Id.create("n1", Node.class),
                         Id.create("n2", Node.class),
                         100, 1000, 13.9, 1));
network.postProcess();
```

## Module Overview

| Package | Contents |
|---------|----------|
| `com.citymodeler.matsim.models.api` | `Coord`, `Id<T>`, `Tuple`, `Attributes` |
| `com.citymodeler.matsim.models.network` | `Network`, `Node`, `Link` |
| `com.citymodeler.matsim.models.lanes` | `Lanes`, `Lane`, `LanesToLinkAssignment` |
| `com.citymodeler.matsim.models.facilities` | `ActivityFacilities`, `ActivityFacility`, `ActivityOption` |
| `com.citymodeler.matsim.models.transit` | `TransitSchedule`, `TransitLine`, `TransitRoute`, `Departure` |
| `com.citymodeler.matsim.models.population` | `Person`, `Plan`, `Activity`, `Leg`, `NetworkRoute`, `TransitPassengerRoute` |
| `com.citymodeler.matsim.models.config` | `Config`, `ConfigGroup`, `ConfigUtils` |
| `com.citymodeler.matsim.models.scenario` | `Scenario`, `ScenarioUtils` |
| `com.citymodeler.matsim.models.io` | XML readers and writers for all models |
| `com.citymodeler.matsim.models.events` | MATSim event types and handlers |
| `com.citymodeler.matsim.models.vehicles` | `Vehicle`, `VehicleType`, `VehicleDefinitions` |
| `com.citymodeler.matsim.models.households` | `Household`, `Households` |

## Domain Table

| Domain | Key Types |
|--------|-----------|
| Network | `Node`, `Link` |
| TransitSchedule | `TransitStopFacility`, `TransitLine`, `TransitRoute`, `Departure` |
| ActivityFacilities | `ActivityFacility`, `ActivityOption` |
| Population | `Person`, `Plan`, `Activity`, `Leg` |
| Config | `Config`, `ConfigGroup` |
| Scenario | `Scenario` (aggregates all domains) |
| Vehicles | `Vehicle`, `VehicleType` |
| Households | `Household`, `Person` |
| Events | `MatsimEvent`, `LinkEnterEvent`, `LinkLeaveEvent`, etc. |

## Design Principles

1. **Apache 2.0 licensed software**: the original code in this repository is reusable by any project (excluding the separately-licensed material described in `THIRD_PARTY_NOTICES.md`).
2. **Java 17**: uses modern Java features (records, sealed classes, pattern matching).
3. **Streaming support**: efficient processing of large files.

## Integration

For applications that need to run MATSim simulation, use this library for file I/O and run
MATSim as a separate JVM process. See [docs/integration-guide.md](docs/integration-guide.md)
for details.

## OpenStreetMap data (ODbL)

This repository ships **no OpenStreetMap data**. The OSM files under
`src/test/resources/fixtures/*.osm` are hand-authored synthetic test fixtures, not extracts
from the OpenStreetMap database.

When the OSM parsing/conversion routines in this library are used on **real OpenStreetMap
data**, the following apply independently of this project's license:

- Imported OSM data remains subject to the **Open Database License (ODbL)**.
- **Attribution to OpenStreetMap contributors** must be preserved where required
  (e.g. `© OpenStreetMap contributors`).
- **Generated/derived datasets** (networks, facility databases, etc.) produced from OSM
  data may carry **ODbL share-alike obligations** that are independent of this software's
  Apache 2.0 license.

## Contributing

Contributions welcome. Please open an issue or PR on GitHub.

## License

Copyright 2026 Ashraf Uz Zaman Patwary. Licensed under the [Apache License, Version
2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).

The Apache 2.0 license applies to the **software written in this repository only**. It does
not apply to OpenStreetMap data, generated OSM-derived databases, the MATSim schema/DTD
files vendored here, or other third-party material. For the licensing of all bundled
third-party items — and for the items whose upstream terms could not be verified — see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
