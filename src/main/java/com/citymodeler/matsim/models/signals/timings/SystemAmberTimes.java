package com.citymodeler.matsim.models.signals.timings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalSystem;

/**
 * Amber timing for one signal system (wire family amberTimes_v1.0, element
 * {@code <signalSystem>} by {@code refId}): optional system-level defaults
 * plus per-SIGNAL overrides (the v1.0 schema overrides at signal level, not
 * group level).
 */
public final class SystemAmberTimes {
    private final Id<SignalSystem> id;
    private final Map<Id<Signal>, AmberIntervals> signalOverrides = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();
    private AmberIntervals systemDefaults;

    public SystemAmberTimes(Id<SignalSystem> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<SignalSystem> getId() {
        return id;
    }

    public AmberIntervals getSystemDefaults() {
        return systemDefaults;
    }

    public void setSystemDefaults(AmberIntervals systemDefaults) {
        this.systemDefaults = systemDefaults;
    }

    public Map<Id<Signal>, AmberIntervals> getSignalOverrides() {
        return Collections.unmodifiableMap(signalOverrides);
    }

    public void addSignalOverride(Id<Signal> signalId, AmberIntervals intervals) {
        Objects.requireNonNull(signalId, "signalId");
        Objects.requireNonNull(intervals, "intervals");
        signalOverrides.put(signalId, intervals);
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
