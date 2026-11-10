package com.citymodeler.matsim.models.signals.timings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalSystem;

/**
 * Root model of the intergreenTimes_v1.0 wire family: per-system group-pair
 * intergreen matrices keyed by signal-system id.
 */
public final class IntergreenTimes {
    private final Map<Id<SignalSystem>, SystemIntergreens> systems = new LinkedHashMap<>();

    public Map<Id<SignalSystem>, SystemIntergreens> getSystems() {
        return Collections.unmodifiableMap(systems);
    }

    public void addSystem(SystemIntergreens system) {
        Objects.requireNonNull(system, "system");
        systems.put(system.getId(), system);
    }
}
