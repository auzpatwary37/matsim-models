package com.citymodeler.matsim.models.population;

import com.citymodeler.matsim.models.api.Attributes;

public final class Leg implements PlanElement {
    private String mode;
    private Route route;
    private final Attributes attributes = new Attributes();

    public Leg(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
