package com.citymodeler.matsim.models.population;

public final class Leg implements PlanElement {
    private String mode;
    private Route route;

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
}
