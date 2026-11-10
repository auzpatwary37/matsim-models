package com.citymodeler.matsim.models.signals.timings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;

/**
 * Intergreen pair matrix for one signal system (wire family
 * intergreenTimes_v1.0, element {@code <signalSystem>} by {@code refId}):
 * seconds between an {@code endingSignalGroup} and a
 * {@code beginningSignalGroup}. Pairs are asymmetric — (g1→g2) and (g2→g1)
 * are independent cells.
 */
public final class SystemIntergreens {
    private final Id<SignalSystem> id;
    private final Map<Id<SignalGroup>, Map<Id<SignalGroup>, Integer>> endingToBeginning = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public SystemIntergreens(Id<SignalSystem> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<SignalSystem> getId() {
        return id;
    }

    public void setEndingToBeginningSec(Id<SignalGroup> ending, Id<SignalGroup> beginning, int sec) {
        Objects.requireNonNull(ending, "ending");
        Objects.requireNonNull(beginning, "beginning");
        if (sec < 0) {
            throw new IllegalArgumentException("intergreen seconds must be >= 0: " + sec);
        }
        endingToBeginning.computeIfAbsent(ending, key -> new LinkedHashMap<>()).put(beginning, sec);
    }

    public Map<Id<SignalGroup>, Map<Id<SignalGroup>, Integer>> getEndingToBeginningSec() {
        Map<Id<SignalGroup>, Map<Id<SignalGroup>, Integer>> copy = new LinkedHashMap<>();
        endingToBeginning.forEach((ending, row) ->
                copy.put(ending, Collections.unmodifiableMap(new LinkedHashMap<>(row))));
        return Collections.unmodifiableMap(copy);
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
