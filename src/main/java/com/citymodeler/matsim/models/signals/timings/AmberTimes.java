package com.citymodeler.matsim.models.signals.timings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalSystem;

/**
 * Root model of the amberTimes_v1.0 wire family: optional global defaults plus
 * per-system amber timing (system defaults and per-signal overrides). The
 * file root IS this object; systems are keyed by signal-system id.
 */
public final class AmberTimes {
    private final Map<Id<SignalSystem>, SystemAmberTimes> systems = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();
    private AmberIntervals globalDefaults;

    public AmberIntervals getGlobalDefaults() {
        return globalDefaults;
    }

    public void setGlobalDefaults(AmberIntervals globalDefaults) {
        this.globalDefaults = globalDefaults;
    }

    public Map<Id<SignalSystem>, SystemAmberTimes> getSystems() {
        return Collections.unmodifiableMap(systems);
    }

    public void addSystem(SystemAmberTimes system) {
        Objects.requireNonNull(system, "system");
        systems.put(system.getId(), system);
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
