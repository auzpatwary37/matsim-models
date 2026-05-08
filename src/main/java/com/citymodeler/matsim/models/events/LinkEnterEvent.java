package com.citymodeler.matsim.models.events;

import java.util.Map;

public final class LinkEnterEvent extends AbstractMatsimEvent {
    public LinkEnterEvent(double time, String type, Map<String, String> attributes) {
        super(time, type, attributes);
    }

    public String getPersonId() {
        return attr("person");
    }

    public String getLinkId() {
        return attr("link");
    }

    public String getVehicleId() {
        return attr("vehicle");
    }
}
