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
        car.setSeatingCapacity(1.0);
        car.setStandingCapacity(3.0);
        car.setLengthMeters(4.5);
        car.setWidthMeters(1.8);
        car.setAccessTimeSeconds(0.5);
        car.setEgressTimeSeconds(0.25);
        definitions.addVehicleType(car);
        definitions.addVehicle(new Vehicle(Id.create("v1", Vehicle.class), "car"));

        String xml = new VehiclesXmlWriter().writeToString(definitions);

        assertTrue(xml.contains("<vehicleType id=\"car\">"), xml);
        assertTrue(xml.contains("seats=\"1.0\""), xml);
        assertTrue(xml.contains("standingRoom=\"3.0\""), xml);
        assertTrue(xml.contains("persons=\"4.0\""), xml);
        assertTrue(xml.contains("<accessTime seconds=\"0.5\"/>"), xml);
        assertTrue(xml.contains("<length meters=\"4.5\"/>"), xml);
        assertTrue(xml.indexOf("<vehicleType") < xml.indexOf("<vehicle "), xml);

        VehicleDefinitions reparsed = new VehiclesXmlReader().read(xml);
        VehicleType reparsedCar = reparsed.getVehicleTypes().get(Id.create("car", VehicleType.class));
        assertEquals(1.0, reparsedCar.getSeatingCapacity());
        assertEquals(3.0, reparsedCar.getStandingCapacity());
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

        assertTrue(xml.contains("<vehicleType id=\"bike\">"), xml);
        assertTrue(xml.contains("seats=\"0.0\""), xml);
        assertTrue(xml.contains("standingRoom=\"0.0\""), xml);
        assertTrue(!xml.contains("<accessTime"), xml);
        assertTrue(!xml.contains("<length"), xml);
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
}