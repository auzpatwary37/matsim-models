package com.citymodeler.matsim.models.osm;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class OsmTagSet {
    private static final OsmTagSet EMPTY = new OsmTagSet(Collections.emptyMap());

    private final Map<String, String> tags;

    private OsmTagSet(Map<String, String> tags) {
        this.tags = tags;
    }

    public static OsmTagSet empty() {
        return EMPTY;
    }

    public static OsmTagSet of(Map<String, String> tags) {
        Objects.requireNonNull(tags, "tags");
        if (tags.isEmpty()) {
            return EMPTY;
        }
        return new OsmTagSet(Collections.unmodifiableSortedMap(new TreeMap<>(tags)));
    }

    public String get(String key) {
        return tags.get(key);
    }

    public boolean has(String key) {
        return tags.containsKey(key);
    }

    public boolean has(String key, String value) {
        return value.equals(tags.get(key));
    }

    public Map<String, String> asMap() {
        return tags;
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OsmTagSet that)) return false;
        return tags.equals(that.tags);
    }

    @Override
    public int hashCode() {
        return tags.hashCode();
    }

    @Override
    public String toString() {
        return "OsmTagSet" + tags;
    }
}
