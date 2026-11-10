package com.citymodeler.matsim.models.signals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

/**
 * The signal groups of one signal system (wire family signalGroups_v2.0,
 * element {@code <signalSystem>}). Group ids are unique per signal system,
 * never globally.
 */
public final class SignalSystemGroups {
    private final Id<SignalSystem> id;
    private final Map<Id<SignalGroup>, SignalGroup> groups = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public SignalSystemGroups(Id<SignalSystem> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<SignalSystem> getId() {
        return id;
    }

    public Map<Id<SignalGroup>, SignalGroup> getGroups() {
        return Collections.unmodifiableMap(groups);
    }

    public void addGroup(SignalGroup group) {
        Objects.requireNonNull(group, "group");
        groups.put(group.getId(), group);
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
