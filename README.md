# MATSim Models

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![CI](https://github.com/auzpatwary37/matsim-models/actions/workflows/ci.yml/badge.svg)](https://github.com/auzpatwary37/matsim-models/actions/workflows/ci.yml)

## Purpose

MATSim Models is a pure Java 17 library providing POJOs and XML I/O for MATSim file formats. It offers a lightweight, standalone way to read, write, and manipulate MATSim XML files without requiring the MATSim runtime library.

## Features

- **MATSim-compatible domain models**: Pure Java POJOs for network, population, transit, config, and events
- **XML readers and writers**: Full support for MATSim 2025.0 XML format
- **No MATSim dependency**: Zero runtime dependencies on MATSim
- **Apache 2.0 licensed**: Reusable by any project
- **Streaming support**: Efficient processing of large event files
- **Modern Java**: Built on Java 17 with records and pattern matching

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

Add the dependency:

```xml
<dependency>
    <groupId>com.citymodeler</groupId>
    <artifactId>matsim-models</artifactId>
    <version>1.0.0</version>
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

## Compatibility Matrix

| matsim-models Version | MATSim Release | XML Schema Version | Status |
|-----------------------|----------------|--------------------|--------|
| 1.0.0 | 2025.0 | 2.0.0 | Current |
| 2.0.0 | Future breaking change | TBD | Planned |

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
| `com.citymodeler.matsim.models.vehicles` | `Vehicle`, `VehicleDefinitions` |
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

1. **No MATSim dependency**: This library has zero runtime dependencies on MATSim.
2. **Apache 2.0 licensed**: Reusable by any project.
3. **Java 17**: Uses modern Java features (records, sealed classes, pattern matching).
4. **Jackson XML**: Uses Jackson for XML binding, not JAXB.
5. **Immutable where possible**: Models favor immutability for thread safety.
6. **Streaming support**: Efficient processing of large files.

## Integration

For applications that need to run MATSim simulation, use `matsim-models` for file I/O and run MATSim as a separate JVM process. See [docs/integration-guide.md](docs/integration-guide.md) for details.

## Contributing

Contributions welcome. Please open an issue or PR on GitHub.

## License

Copyright 2026 Urban Systems Technologies. Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).
