package com.citymodeler.matsim.models.api;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Attributes {
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Object putAttribute(String key, Object value) {
        if (value == null) {
            return removeAttribute(key);
        }
        return attributes.put(key, value);
    }

    public Object removeAttribute(String key) {
        return attributes.remove(key);
    }

    public Map<String, Object> getAsMap() {
        return Collections.unmodifiableMap(Map.copyOf(attributes));
    }
}
