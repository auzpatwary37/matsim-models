package com.citymodeler.matsim.models.facilities;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class ActivityFacilities {
    private String name;
    private final Map<Id<ActivityFacility>, ActivityFacility> facilities = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public ActivityFacilities() {
    }

    public ActivityFacilities(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Id<ActivityFacility>, ActivityFacility> getFacilities() {
        return Collections.unmodifiableMap(facilities);
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void addFacility(ActivityFacility facility) {
        facilities.put(facility.getId(), facility);
    }
}
