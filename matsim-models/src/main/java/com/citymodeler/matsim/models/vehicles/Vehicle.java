package com.citymodeler.matsim.models.vehicles;

import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class Vehicle {
    private final Id<Vehicle> id;
    private String type;
    private final Attributes attributes = new Attributes();

    public Vehicle(Id<Vehicle> id, String type) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
    }

    public Id<Vehicle> getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public Attributes getAttributes() {
        return attributes;
    }
}