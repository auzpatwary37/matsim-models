package com.citymodeler.matsim.models.signals.control;

import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalGroup;

/**
 * One group's flat timing inside a {@link SignalPlan} (wire family
 * signalControl_v2.0, element {@code <signalGroupSettings>}). Onset and
 * dropping are raw second offsets — stage/phase synthesis is a projection-side
 * concern, this model keeps the wire's flat provenance.
 */
public final class SignalGroupSettings {
    private final Id<SignalGroup> signalGroupRef;
    private final int onsetSec;
    private final int droppingSec;
    private final Attributes attributes = new Attributes();

    public SignalGroupSettings(Id<SignalGroup> signalGroupRef, int onsetSec, int droppingSec) {
        this.signalGroupRef = Objects.requireNonNull(signalGroupRef, "signalGroupRef");
        if (onsetSec < 0) {
            throw new IllegalArgumentException("onsetSec must be >= 0: " + onsetSec);
        }
        if (droppingSec < 0) {
            throw new IllegalArgumentException("droppingSec must be >= 0: " + droppingSec);
        }
        this.onsetSec = onsetSec;
        this.droppingSec = droppingSec;
    }

    public Id<SignalGroup> getSignalGroupRef() {
        return signalGroupRef;
    }

    public int getOnsetSec() {
        return onsetSec;
    }

    public int getDroppingSec() {
        return droppingSec;
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
