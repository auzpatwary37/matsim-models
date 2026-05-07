# MATSim Models

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)

## Installing

### From GitHub Packages

```xml
<repositories>
    <repository>
        <id>github-matsim-models</id>
        <url>https://maven.pkg.github.com/citymodeler/matsim-models</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.citymodeler</groupId>
    <artifactId>matsim-models</artifactId>
    <version>1.0.0</version>
</dependency>
```

### From Source

```bash
git clone https://github.com/citymodeler/matsim-models.git
cd matsim-models
mvn clean install
```

### From Source

```bash
git clone https://github.com/citymodeler/matsim-models.git
cd matsim-models
mvn clean install
```

## Quick Start

```java
import com.citymodeler.matsim.models.io.*;
import com.citymodeler.matsim.models.network.*;
import com.citymodeler.matsim.models.transit.*;
import java.nio.file.Path;

// Read a MATSim network
Network network = new NetworkXmlReader().read(Path.of("network.xml"));

// Read transit schedule
TransitSchedule schedule = new TransitScheduleXmlReader().read(Path.of("transitSchedule.xml"));

// Read config
Config config = ConfigUtils.loadConfig(Path.of("config.xml"));

// Write back
new NetworkXmlWriter().write(network, Path.of("output.xml"));
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

## Versioning

`matsim-models` versions track MATSim XML schema versions:

| Version | MATSim Release | Status |
|---------|---------------|--------|
| 1.0.0 | 2025.0 | Current |
| 2.0.0 | Breaking schema change | Future |

## Contributing

Contributions welcome. Please open an issue or PR on GitHub.

## License

Copyright 2026 Urban Systems Technologies. Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).
