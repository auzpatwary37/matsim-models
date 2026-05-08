package com.citymodeler.matsim.models.transit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class TransitLine {
    private final Id<TransitLine> id;
    private String name;
    private final Map<Id<TransitRoute>, TransitRoute> routes = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public TransitLine(Id<TransitLine> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<TransitLine> getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Id<TransitRoute>, TransitRoute> getRoutes() {
        return Collections.unmodifiableMap(routes);
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void addRoute(TransitRoute route) {
        Objects.requireNonNull(route, "route");
        routes.put(route.getId(), route);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransitLine that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
