package com.citymodeler.matsim.models.signals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

/**
 * One traffic signal system (wire family signalSystems_v2.0, element
 * {@code <signalSystem>}), grouping the signals that belong to a single
 * intersection or coordinated corridor. Signal ids are unique per signal
 * system, never globally.
 */
public final class SignalSystem {
    private final Id<SignalSystem> id;
    private final Map<Id<Signal>, Signal> signals = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public SignalSystem(Id<SignalSystem> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<SignalSystem> getId() {
        return id;
    }

    public Map<Id<Signal>, Signal> getSignals() {
        return Collections.unmodifiableMap(signals);
    }

    public void addSignal(Signal signal) {
        Objects.requireNonNull(signal, "signal");
        signals.put(signal.getId(), signal);
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
