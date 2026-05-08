package com.citymodeler.matsim.models.events;

import java.util.Map;

public final class DepartureEvent extends AbstractMatsimEvent {
    public DepartureEvent(double time, String type, Map<String, String> attributes) {
        super(time, type, attributes);
    }

    public String getPersonId() {
        return attr("person");
    }

    public String getLinkId() {
        return attr("link");
    }

    public String getLegMode() {
        return attr("legMode");
    }
}
