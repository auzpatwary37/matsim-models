package com.citymodeler.matsim.models.population;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class UnknownRoute implements Route {
    private final String routeType;
    private String text;
    private final Map<String, String> attributes = new HashMap<>();
    private final Map<String, String> children = new HashMap<>();

    public UnknownRoute(String routeType) {
        this.routeType = Objects.requireNonNull(routeType, "routeType");
    }

    public String getRouteType() {
        return routeType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public void setAttribute(String key, String value) {
        attributes.put(Objects.requireNonNull(key), value);
    }

    public Map<String, String> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    public void setChild(String key, String value) {
        children.put(Objects.requireNonNull(key), value);
    }
}