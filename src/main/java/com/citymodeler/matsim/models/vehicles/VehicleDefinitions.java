package com.citymodeler.matsim.models.vehicles;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.citymodeler.matsim.models.api.Id;

public final class VehicleDefinitions {
    private final Map<Id<Vehicle>, Vehicle> vehicles = new LinkedHashMap<>();
    private final Map<Id<VehicleType>, VehicleType> vehicleTypes = new TreeMap<>(
            Comparator.comparing(id -> id.toString()));

    public void addVehicle(Vehicle vehicle) {
        if (vehicle == null) {
            throw new NullPointerException("vehicle");
        }
        vehicles.put(vehicle.getId(), vehicle);
    }

    public void addVehicleType(VehicleType vehicleType) {
        if (vehicleType == null) {
            throw new NullPointerException("vehicleType");
        }
        if (vehicleTypes.containsKey(vehicleType.getId())) {
            throw new IllegalArgumentException("Duplicate vehicle type id: " + vehicleType.getId());
        }
        vehicleTypes.put(vehicleType.getId(), vehicleType);
    }

    public Map<Id<Vehicle>, Vehicle> getVehicles() {
        return Collections.unmodifiableMap(vehicles);
    }

    public Map<Id<VehicleType>, VehicleType> getVehicleTypes() {
        return Collections.unmodifiableMap(vehicleTypes);
    }
}