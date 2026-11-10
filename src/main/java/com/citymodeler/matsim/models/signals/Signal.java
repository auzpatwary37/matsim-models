package com.citymodeler.matsim.models.signals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

/**
 * One traffic signal of a signal system (wire family signalSystems_v2.0,
 * element {@code <signal>}). The controlled link reference is REQUIRED on the
 * wire (linkIdRef) and is the position/movement anchor the GUI projection
 * infers intersection position from. Lane references further restrict the
 * controlled lane(s); turningMoveRestrictions narrows allowed turns.
 * Signal ids are unique per signal system, never globally.
 */
public final class Signal {
    private final Id<Signal> id;
    private final Id<Link> linkIdRef;
    private final List<Id<com.citymodeler.matsim.models.lanes.Lane>> laneRefs = new ArrayList<>();
    private final TurningMoveRestrictions restrictions = new TurningMoveRestrictions();
    private final Attributes attributes = new Attributes();

    public Signal(Id<Signal> id, Id<Link> linkIdRef) {
        this.id = Objects.requireNonNull(id, "id");
        this.linkIdRef = Objects.requireNonNull(linkIdRef, "linkIdRef");
    }

    public Id<Signal> getId() {
        return id;
    }

    public Id<Link> getLinkIdRef() {
        return linkIdRef;
    }

    public List<Id<com.citymodeler.matsim.models.lanes.Lane>> getLaneRefs() {
        return Collections.unmodifiableList(laneRefs);
    }

    public void addLaneRef(Id<com.citymodeler.matsim.models.lanes.Lane> laneRef) {
        laneRefs.add(Objects.requireNonNull(laneRef, "laneRef"));
    }

    public TurningMoveRestrictions getRestrictions() {
        return restrictions;
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
