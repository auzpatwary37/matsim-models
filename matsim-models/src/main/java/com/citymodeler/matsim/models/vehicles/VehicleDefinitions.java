package com.citymodeler.matsim.models.vehicles;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.citymodeler.matsim.models.api.Id;

public final class VehicleDefinitions {
    private final Map<Id<Vehicle>, Vehicle> vehicles = new LinkedHashMap<>();

    public void addVehicle(Vehicle vehicle) {
        if (vehicle == null) {
            throw new NullPointerException("vehicle");
        }
        vehicles.put(vehicle.getId(), vehicle);
    }

    public Map<Id<Vehicle>, Vehicle> getVehicles() {
        return Collections.unmodifiableMap(vehicles);
    }
}