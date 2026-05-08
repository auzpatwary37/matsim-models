package com.citymodeler.matsim.models.vehicles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;

class VehicleTest {
    @Test
    void createsVehicleWithIdAndType() {
        Vehicle vehicle = new Vehicle(Id.create("v1", Vehicle.class), "car");
        assertEquals("v1", vehicle.getId().toString());
        assertEquals("car", vehicle.getType());
        assertNotNull(vehicle.getAttributes());
        assertTrue(vehicle.getAttributes().getAsMap().isEmpty());
    }

    @Test
    void allowsMutatingType() {
        Vehicle vehicle = new Vehicle(Id.create("v1", Vehicle.class), "car");
        vehicle.setType("bike");
        assertEquals("bike", vehicle.getType());
    }

    @Test
    void allowsAttributesManipulation() {
        Vehicle vehicle = new Vehicle(Id.create("v1", Vehicle.class), "car");
        vehicle.getAttributes().putAttribute("color", "blue");
        assertEquals("blue", vehicle.getAttributes().getAttribute("color"));
    }

    @Test
    void getAttributesReturnsUnmodifiableView() {
        Vehicle vehicle = new Vehicle(Id.create("v1", Vehicle.class), "car");
        vehicle.getAttributes().putAttribute("color", "blue");
        assertThrows(UnsupportedOperationException.class, () -> vehicle.getAttributes().getAsMap().clear());
    }
}

class VehicleDefinitionsTest {
    @Test
    void addVehicleMakesVehicleAccessible() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        Vehicle vehicle = new Vehicle(Id.create("v1", Vehicle.class), "car");
        definitions.addVehicle(vehicle);
        assertEquals(1, definitions.getVehicles().size());
        assertEquals(vehicle, definitions.getVehicles().get(Id.create("v1", Vehicle.class)));
    }

    @Test
    void getVehiclesReturnsUnmodifiableView() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        Vehicle vehicle = new Vehicle(Id.create("v1", Vehicle.class), "car");
        definitions.addVehicle(vehicle);
        assertThrows(UnsupportedOperationException.class, () -> definitions.getVehicles().clear());
    }

    @Test
    void getVehiclesReturnsUnmodifiableMap() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        Vehicle vehicle = new Vehicle(Id.create("v1", Vehicle.class), "car");
        definitions.addVehicle(vehicle);
        Map<Id<Vehicle>, Vehicle> vehicles = definitions.getVehicles();
        assertThrows(UnsupportedOperationException.class, () -> vehicles.put(
                Id.create("v2", Vehicle.class), vehicle));
    }

    @Test
    void multipleVehiclesAreAccessible() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        definitions.addVehicle(new Vehicle(Id.create("v1", Vehicle.class), "car"));
        definitions.addVehicle(new Vehicle(Id.create("v2", Vehicle.class), "bike"));
        assertEquals(2, definitions.getVehicles().size());
    }

    @Test
    void addVehicleWithNullIsRejected() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        assertThrows(NullPointerException.class, () -> definitions.addVehicle(null));
    }
}