package com.citymodeler.matsim.models.vehicles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.VehiclesXmlReader;
import com.citymodeler.matsim.models.io.VehiclesXmlWriter;

class VehiclesXmlTest {
    @Test
    void readsWritesAndReadsVehiclesXml() {
        String xml = """
                <vehicles>
                    <vehicle id="v1" type="car" />
                    <vehicle id="v2" type="bike" />
                </vehicles>
                """;

        VehicleDefinitions definitions = new VehiclesXmlReader().read(xml);
        assertEquals(2, definitions.getVehicles().size());

        Vehicle v1 = definitions.getVehicles().get(Id.create("v1", Vehicle.class));
        assertEquals("car", v1.getType());
        assertNull(v1.getAttributes().getAttribute("color"));

        Vehicle v2 = definitions.getVehicles().get(Id.create("v2", Vehicle.class));
        assertEquals("bike", v2.getType());
    }

    @Test
    void roundTripPreservesVehicleData() {
        String xml = """
                <vehicles>
                    <vehicle id="v1" type="car" />
                </vehicles>
                """;

        VehicleDefinitions definitions = new VehiclesXmlReader().read(xml);
        String roundTrippedXml = new VehiclesXmlWriter().writeToString(definitions);
        VehicleDefinitions roundTripped = new VehiclesXmlReader().read(roundTrippedXml);

        assertEquals(1, roundTripped.getVehicles().size());
        Vehicle v1 = roundTripped.getVehicles().get(Id.create("v1", Vehicle.class));
        assertEquals("car", v1.getType());
    }

    @Test
    void readFromClasspathFixture() {
        String fixturePath = "fixtures/vehicles.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        VehicleDefinitions definitions = new VehiclesXmlReader().read(is);

        assertNotNull(definitions);
        assertEquals(2, definitions.getVehicles().size());

        Vehicle v1 = definitions.getVehicles().get(Id.create("v1", Vehicle.class));
        assertEquals("car", v1.getType());

        Vehicle v2 = definitions.getVehicles().get(Id.create("v2", Vehicle.class));
        assertEquals("bike", v2.getType());
    }

    @Test
    void readVehiclesWithAttributes() {
        String xml = """
                <vehicles>
                    <vehicle id="v1" type="car">
                        <attributes>
                            <attribute name="color" class="java.lang.String">blue</attribute>
                        </attributes>
                    </vehicle>
                </vehicles>
                """;

        VehicleDefinitions definitions = new VehiclesXmlReader().read(xml);
        Vehicle v1 = definitions.getVehicles().get(Id.create("v1", Vehicle.class));
        assertEquals("blue", v1.getAttributes().getAttribute("color"));
    }

    @Test
    void roundTripsVehicleTypesWithCapacitiesAndDimensions() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        VehicleType car = new VehicleType(Id.create("car", VehicleType.class));
        car.setSeatingCapacity(1);
        car.setStandingCapacity(3);
        car.setLengthMeters(4.5);
        car.setWidthMeters(1.8);
        car.setAccessTimeSeconds(0.5);
        car.setEgressTimeSeconds(0.25);
        definitions.addVehicleType(car);
        definitions.addVehicle(new Vehicle(Id.create("v1", Vehicle.class), "car"));

        String xml = new VehiclesXmlWriter().writeToString(definitions);

        // Canonical MATSim vehicleDefinitions v2.0 wire form.
        assertTrue(xml.contains("<vehicleDefinitions"), xml);
        assertTrue(xml.contains("vehicleDefinitions_v2.0.xsd"), xml);
        assertTrue(xml.contains("<vehicleType id=\"car\">"), xml);
        assertTrue(xml.contains("seats=\"1\""), xml);
        assertTrue(xml.contains("standingRoomInPersons=\"3\""), xml);
        assertTrue(xml.contains("<length meter=\"4.5\"/>"), xml);
        assertTrue(xml.contains("<width meter=\"1.8\"/>"), xml);
        assertTrue(xml.contains("accessTimeInSecondsPerPerson"), xml);
        assertTrue(xml.contains("egressTimeInSecondsPerPerson"), xml);
        assertTrue(xml.indexOf("<vehicleType") < xml.indexOf("<vehicle "), xml);

        VehicleDefinitions reparsed = new VehiclesXmlReader().read(xml);
        VehicleType reparsedCar = reparsed.getVehicleTypes().get(Id.create("car", VehicleType.class));
        assertEquals(1, reparsedCar.getSeatingCapacity());
        assertEquals(3, reparsedCar.getStandingCapacity());
        assertEquals(4.5, reparsedCar.getLengthMeters());
        assertEquals(1.8, reparsedCar.getWidthMeters());
        assertEquals(0.5, reparsedCar.getAccessTimeSeconds());
        assertEquals(0.25, reparsedCar.getEgressTimeSeconds());
        assertEquals("car", reparsed.getVehicles().get(Id.create("v1", Vehicle.class)).getType());

        assertEquals(xml, new VehiclesXmlWriter().writeToString(reparsed));
    }

    @Test
    void writesMinimalVehicleTypeWithoutOptionalElements() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        definitions.addVehicleType(new VehicleType(Id.create("bike", VehicleType.class)));

        String xml = new VehiclesXmlWriter().writeToString(definitions);

        assertTrue(xml.contains("<vehicleType id=\"bike\""), xml);
        assertTrue(!xml.contains("<capacity"), xml);
        assertTrue(!xml.contains("accessTimeInSecondsPerPerson"), xml);
        assertTrue(!xml.contains("<length"), xml);
        assertTrue(!xml.contains("<width"), xml);
    }

    @Test
    void readerStillParsesHistoricVehiclesDialect() {
        String xml = """
                <vehicles>
                    <vehicleType id="bus">
                        <capacity seats="40" standingRoom="60" persons="100"/>
                        <accessTime seconds="1.5"/>
                        <egressTime seconds="0.75"/>
                        <length meters="12.0"/>
                        <width meters="2.5"/>
                    </vehicleType>
                    <vehicle id="v1" type="bus"/>
                </vehicles>
                """;

        VehicleDefinitions definitions = new VehiclesXmlReader().read(xml);

        VehicleType bus = definitions.getVehicleTypes().get(Id.create("bus", VehicleType.class));
        assertEquals(40, bus.getSeatingCapacity());
        assertEquals(60, bus.getStandingCapacity());
        assertEquals(1.5, bus.getAccessTimeSeconds());
        assertEquals(0.75, bus.getEgressTimeSeconds());
        assertEquals(12.0, bus.getLengthMeters());
        assertEquals(2.5, bus.getWidthMeters());
        assertEquals("bus", definitions.getVehicles().get(Id.create("v1", Vehicle.class)).getType());
    }

    @Test
    void writeVehiclesWithAttributes() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        Vehicle vehicle = new Vehicle(Id.create("v1", Vehicle.class), "car");
        vehicle.getAttributes().putAttribute("color", "red");
        definitions.addVehicle(vehicle);

        String xml = new VehiclesXmlWriter().writeToString(definitions);
        VehicleDefinitions roundTripped = new VehiclesXmlReader().read(xml);

        Vehicle v1 = roundTripped.getVehicles().get(Id.create("v1", Vehicle.class));
        assertEquals("red", v1.getAttributes().getAttribute("color"));
    }

    @Test
    void capacityOtherRoundTrips() {
        String xml = """
                <vehicles>
                    <vehicleType id="bus">
                        <capacity seats="40" other="2.5"/>
                    </vehicleType>
                    <vehicle id="v1" type="bus"/>
                </vehicles>
                """;
        VehicleDefinitions defs = new VehiclesXmlReader().read(xml);
        VehicleType bus = defs.getVehicleTypes().get(Id.create("bus", VehicleType.class));
        assertEquals("2.5", bus.getCapacityOther());

        String out = new VehiclesXmlWriter().writeToString(defs);
        assertTrue(out.contains("other=\"2.5\""), out);

        VehicleDefinitions reRead = new VehiclesXmlReader().read(out);
        assertEquals("2.5", reRead.getVehicleTypes().get(Id.create("bus", VehicleType.class)).getCapacityOther());
    }

    @Test
    void effectiveAccessTimeDefaultsToOneSecondWhenUnset() {
        VehicleType vt = new VehicleType(Id.create("car", VehicleType.class));
        assertNull(vt.getAccessTimeSeconds());
        assertEquals(1.0, vt.getEffectiveAccessTimeSeconds());
        assertNull(vt.getEgressTimeSeconds());
        assertEquals(1.0, vt.getEffectiveEgressTimeSeconds());

        vt.setAccessTimeSeconds(2.5);
        assertEquals(2.5, vt.getEffectiveAccessTimeSeconds());
    }

    @Test
    void unknownChildElementsSurviveRoundTrip() {
        String xml = """
                <vehicles>
                    <vehicleType id="bus">
                        <capacity seats="40"/>
                        <doorOperationMode>automatic</doorOperationMode>
                        <maximumVelocity unit="m/s">20.0</maximumVelocity>
                    </vehicleType>
                    <vehicle id="v1" type="bus"/>
                </vehicles>
                """;
        VehicleDefinitions defs = new VehiclesXmlReader().read(xml);
        VehicleType bus = defs.getVehicleTypes().get(Id.create("bus", VehicleType.class));
        assertEquals(2, bus.getExtensionElements().size());

        String out = new VehiclesXmlWriter().writeToString(defs);
        assertTrue(out.contains("doorOperationMode"), out);
        assertTrue(out.contains("maximumVelocity"), out);

        VehicleDefinitions reRead = new VehiclesXmlReader().read(out);
        assertEquals(2, reRead.getVehicleTypes().get(Id.create("bus", VehicleType.class)).getExtensionElements().size());
    }
}