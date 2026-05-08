package com.citymodeler.matsim.models.events;

import java.util.Map;

public final class ActivityStartEvent extends AbstractMatsimEvent {
    public ActivityStartEvent(double time, String type, Map<String, String> attributes) {
        super(time, type, attributes);
    }

    public String getPersonId() {
        return attr("person");
    }

    public String getLinkId() {
        return attr("link");
    }

    public String getFacilityId() {
        return attr("facility");
    }

    public String getActivityType() {
        return attr("actType");
    }
}
