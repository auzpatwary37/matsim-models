package com.citymodeler.matsim.models.facilities;

import java.util.Objects;

public final class ActivityOption {
    private String type;
    private double capacity;

    public ActivityOption(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public double getCapacity() {
        return capacity;
    }

    public void setCapacity(double capacity) {
        this.capacity = capacity;
    }
}
