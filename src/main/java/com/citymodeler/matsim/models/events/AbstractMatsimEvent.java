package com.citymodeler.matsim.models.events;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

abstract class AbstractMatsimEvent implements MatsimEvent {
    private final double time;
    private final String type;
    private final Map<String, String> attributes;

    AbstractMatsimEvent(double time, String type, Map<String, String> attributes) {
        this.time = time;
        this.type = Objects.requireNonNull(type, "type");
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(attributes, "attributes")));
    }

    @Override
    public double getTime() {
        return time;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Map<String, String> getAttributes() {
        return attributes;
    }

    String attr(String name) {
        return attributes.get(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractMatsimEvent that)) return false;
        return Double.compare(that.time, time) == 0 &&
               type.equals(that.type) &&
               attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, type, attributes);
    }
}
