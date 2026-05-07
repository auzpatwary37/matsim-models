# MATSim Models

A standalone Java 17 library providing data models for MATSim-related workflows. This library contains only pure Java domain models and XML serialization/deserialization - it has **no dependency on MATSim itself**.

## Purpose

This library enables CityModeler to work with MATSim scenarios without requiring the full MATSim framework. It provides:

- Network, lanes, facilities, transit schedule, population, and config domain models
- XML readers and writers for all model types
- Round-trip fidelity for MATSim XML formats

## Quick Start

```java
// Read a MATSim network from a file
Network network = new NetworkXmlReader().read(Path.of("network.xml"));

// Read transit schedule
TransitSchedule schedule = new TransitScheduleXmlReader().read(Path.of("transitSchedule.xml"));

// Read population
Population population = new PopulationXmlReader().read(Path.of("population.xml"));
```

## License

This library is licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).