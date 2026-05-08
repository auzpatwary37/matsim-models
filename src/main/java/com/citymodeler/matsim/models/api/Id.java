package com.citymodeler.matsim.models.api;

import java.util.Objects;

public final class Id<T> {
    private final String id;

    private Id(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static <T> Id<T> create(String id, Class<T> type) {
        return new Id<>(id);
    }

    public static Id<com.citymodeler.matsim.models.network.Link> createLinkId(String id) {
        return new Id<>(id);
    }

    /**
     * Equality is based solely on the string id value, not the type parameter.
     * This means Id&lt;Link&gt;("1").equals(Id&lt;Node&gt;("1")) returns true.
     * This matches MATSim's Id.equals behavior for compatibility.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Id<?> other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
