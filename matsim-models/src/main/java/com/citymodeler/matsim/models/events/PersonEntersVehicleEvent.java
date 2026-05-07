package com.citymodeler.matsim.models.events;

import java.util.Map;

public final class PersonEntersVehicleEvent extends AbstractMatsimEvent {
    public PersonEntersVehicleEvent(double time, String type, Map<String, String> attributes) {
        super(time, type, attributes);
    }

    public String getPersonId() {
        return attr("person");
    }

    public String getVehicleId() {
        return attr("vehicle");
    }
}
