# Integration Guide

This guide explains how applications can integrate `matsim-models` to read, write, and manipulate MATSim XML files without requiring the MATSim runtime library.

## Overview

`matsim-models` provides:
- Pure Java POJOs for MATSim domain models
- XML readers and writers compatible with MATSim 2025.0 format
- No runtime dependency on MATSim
- Apache 2.0 licensed components

## Use Cases

### In-Process Model Layer

Applications can use `matsim-models` as an in-process model layer for:
- Loading and saving MATSim XML files (network, population, transit, etc.)
- Programmatic scenario construction and modification
- Event file parsing and analysis
- Configuration file manipulation

### External MATSim Execution

For applications that need to run MATSim simulation:
- Use `matsim-models` for file I/O and model manipulation
- Run MATSim as a separate JVM process for simulation execution
- Exchange data via XML files between your application and MATSim

This architecture keeps concerns separated:
- Your application handles UI, data editing, and analysis
- MATSim handles simulation execution
- XML files serve as the interchange format

## Replacing MATSim Imports

### Network

**Before:**
```java
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.io.NetworkReader;
```

**After:**
```java
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.io.NetworkXmlReader;

Network network = new NetworkXmlReader().read(Path.of("network.xml"));
```

### Population

**Before:**
```java
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.io.PopulationReader;
```

**After:**
```java
import com.citymodeler.matsim.models.population.Population;
import com.citymodeler.matsim.models.io.PopulationXmlReader;

Population population = new PopulationXmlReader().read(Path.of("population.xml"));
```

### Config

**Before:**
```java
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
```

**After:**
```java
import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.config.ConfigUtils;

Config config = ConfigUtils.loadConfig(Path.of("config.xml"));
```

## Running MATSim Externally

When your application needs to execute MATSim simulation:

```java
// 1. Prepare input files using matsim-models
Network network = new NetworkXmlReader().read(Path.of("input-network.xml"));
// ... modify network ...
new NetworkXmlWriter().write(network, Path.of("runs/network.xml"));

// 2. Run MATSim as external process
ProcessBuilder pb = new ProcessBuilder(
    "java", "-Xmx4g",
    "-cp", "matsim.jar",
    "org.matsim.core.controler.Controler",
    "runs/config.xml"
);
Process process = pb.start();
process.waitFor();

// 3. Read output files using matsim-models
Population output = new PopulationXmlReader().read(Path.of("runs/output_plans.xml.gz"));
```

## Event Streaming

For large event files, use streaming processing:

```java
import com.citymodeler.matsim.models.io.EventsXmlReader;
import com.citymodeler.matsim.models.events.MatsimEvent;

try (Stream<MatsimEvent> events = new EventsXmlReader().stream(Path.of("events.xml.gz"))) {
    long linkEnters = events
        .filter(e -> e instanceof LinkEnterEvent)
        .count();
    System.out.println("Link enter events: " + linkEnters);
}
```

## API Reference

| Domain | matsim-models Package | Key Types |
|--------|----------------------|-----------|
| Network | `com.citymodeler.matsim.models.network` | `Network`, `Node`, `Link` |
| Population | `com.citymodeler.matsim.models.population` | `Population`, `Person`, `Plan`, `Activity`, `Leg` |
| Transit | `com.citymodeler.matsim.models.transit` | `TransitSchedule`, `TransitLine`, `TransitRoute` |
| Config | `com.citymodeler.matsim.models.config` | `Config`, `ConfigGroup` |
| Events | `com.citymodeler.matsim.models.events` | `MatsimEvent`, `LinkEnterEvent`, `DepartureEvent` |
| IO | `com.citymodeler.matsim.models.io` | `*XmlReader`, `*XmlWriter` |

## Programmatic Construction

When building models in code:

```java
Network network = new Network();
network.addNode(new Node(Id.create("n1", Node.class), new Coord(0, 0)));
network.addNode(new Node(Id.create("n2", Node.class), new Coord(100, 0)));
network.addLink(new Link(
    Id.create("l1", Link.class),
    Id.create("n1", Node.class),
    Id.create("n2", Node.class),
    100.0,  // length
    1000.0, // capacity
    13.9,   // freespeed
    1       // lanes
));
network.postProcess(); // Call after all modifications
```

## Compatibility

| matsim-models | MATSim Release | XML Format |
|---------------|----------------|------------|
| 1.0.0 | 2025.0 | Supported |

## Maven Dependency

```xml
<dependency>
    <groupId>com.citymodeler</groupId>
    <artifactId>matsim-models</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Support

For issues or questions, open an issue on the [GitHub repository](https://github.com/auzpatwary37/matsim-models).
