package com.citymodeler.matsim.models.signals;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

/**
 * One signal group of a signal system (wire family signalGroups_v2.0, element
 * {@code <signalGroup>}), naming the signals whose movements switch together.
 * Signal references keep insertion order; group ids are unique per signal
 * system, never globally.
 */
public final class SignalGroup {
    private final Id<SignalGroup> id;
    private final Set<Id<Signal>> signalRefIds = new LinkedHashSet<>();
    private final Attributes attributes = new Attributes();

    public SignalGroup(Id<SignalGroup> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<SignalGroup> getId() {
        return id;
    }

    public Set<Id<Signal>> getSignalRefIds() {
        return Collections.unmodifiableSet(signalRefIds);
    }

    public void addSignalRef(Id<Signal> signalRef) {
        signalRefIds.add(Objects.requireNonNull(signalRef, "signalRef"));
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
