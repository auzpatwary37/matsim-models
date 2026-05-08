package com.citymodeler.matsim.models.events;

import java.util.Map;

public interface MatsimEvent {
    double getTime();

    String getType();

    Map<String, String> getAttributes();
}
