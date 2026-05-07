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
