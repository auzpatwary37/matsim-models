package com.citymodeler.matsim.models.vehicles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;

class VehicleTypeTest {

    @Test
    void defaultsAreZero() {
        VehicleType type = new VehicleType(Id.create("car", VehicleType.class));
        assertEquals(0.0, type.getSeatingCapacity());
        assertEquals(0.0, type.getStandingCapacity());
        assertEquals(0.0, type.getLengthMeters());
        assertEquals(0.0, type.getWidthMeters());
        assertEquals(0.0, type.getAccessTimeSeconds());
        assertEquals(0.0, type.getEgressTimeSeconds());
    }

    @Test
    void settersAcceptValidValues() {
        VehicleType type = new VehicleType(Id.create("bus", VehicleType.class));
        type.setSeatingCapacity(40.0);
        type.setStandingCapacity(60.0);
        type.setLengthMeters(12.0);
        type.setWidthMeters(2.5);
        type.setAccessTimeSeconds(1.5);
        type.setEgressTimeSeconds(0.75);

        assertEquals(40.0, type.getSeatingCapacity());
        assertEquals(60.0, type.getStandingCapacity());
        assertEquals(12.0, type.getLengthMeters());
        assertEquals(2.5, type.getWidthMeters());
        assertEquals(1.5, type.getAccessTimeSeconds());
        assertEquals(0.75, type.getEgressTimeSeconds());
    }

    @Test
    void rejectsNegativeCapacities() {
        VehicleType type = new VehicleType(Id.create("car", VehicleType.class));
        assertThrows(IllegalArgumentException.class, () -> type.setSeatingCapacity(-0.1));
        assertThrows(IllegalArgumentException.class, () -> type.setStandingCapacity(-1.0));
    }

    @Test
    void rejectsNegativeDimensionsAndTimesWhenSet() {
        VehicleType type = new VehicleType(Id.create("car", VehicleType.class));
        assertThrows(IllegalArgumentException.class, () -> type.setLengthMeters(-1.0));
        assertThrows(IllegalArgumentException.class, () -> type.setWidthMeters(-0.5));
        assertThrows(IllegalArgumentException.class, () -> type.setAccessTimeSeconds(-1.0));
        assertThrows(IllegalArgumentException.class, () -> type.setEgressTimeSeconds(-0.25));
    }

    @Test
    void duplicateTypeIdsAreRejected() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        definitions.addVehicleType(new VehicleType(Id.create("car", VehicleType.class)));
        assertThrows(IllegalArgumentException.class,
                () -> definitions.addVehicleType(new VehicleType(Id.create("car", VehicleType.class))));
    }

    @Test
    void typesAreReturnedSortedByIdAndUnmodifiable() {
        VehicleDefinitions definitions = new VehicleDefinitions();
        definitions.addVehicleType(new VehicleType(Id.create("zeta", VehicleType.class)));
        definitions.addVehicleType(new VehicleType(Id.create("alpha", VehicleType.class)));

        assertEquals(
                List.of(Id.create("alpha", VehicleType.class), Id.create("zeta", VehicleType.class)),
                List.copyOf(definitions.getVehicleTypes().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> definitions.getVehicleTypes().put(Id.create("beta", VehicleType.class),
                        new VehicleType(Id.create("beta", VehicleType.class))));
    }
}
