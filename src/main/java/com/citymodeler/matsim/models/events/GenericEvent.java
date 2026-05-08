package com.citymodeler.matsim.models.events;

import java.util.Map;

public final class GenericEvent extends AbstractMatsimEvent {
    public GenericEvent(double time, String type, Map<String, String> attributes) {
        super(time, type, attributes);
    }
}
