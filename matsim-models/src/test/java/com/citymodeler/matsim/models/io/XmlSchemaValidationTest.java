package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class XmlSchemaValidationTest {

    @Test
    void validatesKnownFixturesWhenEnabled() {
        assertDoesNotThrow(() -> new NetworkXmlReader(true).read(fixture("network.xml")));
        assertDoesNotThrow(() -> new ConfigXmlReader(true).read(fixture("config.xml")));
        assertDoesNotThrow(() -> new FacilitiesXmlReader(true).read(fixture("facilities.xml")));
        assertDoesNotThrow(() -> new PopulationXmlReader(true).read(fixture("population.xml")));
        assertDoesNotThrow(() -> new TransitScheduleXmlReader(true).read(fixture("transitSchedule.xml")));
        assertDoesNotThrow(() -> new LanesXmlReader(true).read(fixture("lanes.xml")));
        assertDoesNotThrow(() -> new VehiclesXmlReader(true).read(fixture("vehicles.xml")));
        assertDoesNotThrow(() -> new HouseholdsXmlReader(true).read(fixture("households.xml")));
        assertDoesNotThrow(() -> new EventsXmlReader(true).read(fixture("events.xml"), event -> { }));
    }

    @Test
    void rejectsInvalidRootWhenValidationEnabled() {
        assertThrows(MatsimValidationException.class, () -> new NetworkXmlReader(true).read("<notNetwork/>"));
        assertThrows(MatsimValidationException.class, () -> new ConfigXmlReader(true).readString("<notConfig/>"));
        assertThrows(MatsimValidationException.class, () -> new FacilitiesXmlReader(true).read("<notFacilities/>"));
        assertThrows(MatsimValidationException.class, () -> new PopulationXmlReader(true).readString("<notPopulation/>"));
        assertThrows(MatsimValidationException.class, () -> new TransitScheduleXmlReader(true).read("<notTransit/>"));
        assertThrows(MatsimValidationException.class, () -> new LanesXmlReader(true).read("<notLanes/>"));
        assertThrows(MatsimValidationException.class, () -> new VehiclesXmlReader(true).read("<notVehicles/>"));
        assertThrows(MatsimValidationException.class, () -> new HouseholdsXmlReader(true).read("<notHouseholds/>"));
        assertThrows(MatsimValidationException.class, () -> new EventsXmlReader(true).readString("<notEvents/>", event -> { }));
    }

    private Path fixture(String name) {
        return Path.of("src/test/resources/fixtures", name);
    }
}
