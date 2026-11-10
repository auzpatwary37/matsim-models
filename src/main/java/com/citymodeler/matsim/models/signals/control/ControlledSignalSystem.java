package com.citymodeler.matsim.models.signals.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalSystem;

/**
 * One controlled signal system (wire family signalControl_v2.0, element
 * {@code <signalSystem>} inside {@code <signalControl>}). The
 * {@code controllerIdentifier} names the actuation controller and is required
 * by the published schema whenever a {@code signalSystemController} element is
 * present. Plans are kept as a file-ordered LIST: plans may be id-less, so
 * stable identity is a projection-side concern and the writer only has to keep
 * order.
 */
public final class ControlledSignalSystem {
    private final Id<SignalSystem> id;
    private final List<SignalPlan> plans = new ArrayList<>();
    private final Attributes attributes = new Attributes();
    private String controllerIdentifier;

    public ControlledSignalSystem(Id<SignalSystem> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<SignalSystem> getId() {
        return id;
    }

    public String getControllerIdentifier() {
        return controllerIdentifier;
    }

    public void setControllerIdentifier(String controllerIdentifier) {
        this.controllerIdentifier = controllerIdentifier;
    }

    public List<SignalPlan> getPlans() {
        return Collections.unmodifiableList(plans);
    }

    public void addPlan(SignalPlan plan) {
        Objects.requireNonNull(plan, "plan");
        plans.add(plan);
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
