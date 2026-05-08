package com.citymodeler.matsim.models.vehicles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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