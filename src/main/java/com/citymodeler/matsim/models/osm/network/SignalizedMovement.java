package com.citymodeler.matsim.models.osm.network;

import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.Objects;

/** A controlled turning movement at a signalized junction: incoming link to outgoing link. */
public record SignalizedMovement(
        String incomingLinkId,
        String outgoingLinkId,
        OsmTurnType turnType,
        Set<String> controlledModes,
        Set<String> restrictedModes) {

    public SignalizedMovement {
        incomingLinkId = Objects.requireNonNull(incomingLinkId, "incomingLinkId");
        outgoingLinkId = Objects.requireNonNull(outgoingLinkId, "outgoingLinkId");
        turnType = Objects.requireNonNull(turnType, "turnType");
        controlledModes = Set.copyOf(new TreeSet<>(controlledModes));
        restrictedModes = Set.copyOf(new TreeSet<>(restrictedModes));
    }

    /** Modes that are controlled here and not subject to a turn restriction. */
    public Set<String> legalModes() {
        Set<String> legal = new TreeSet<>(controlledModes);
        legal.removeAll(restrictedModes);
        return legal;
    }

    /**
     * True when at least one controlled mode is legal. Review: this is the right predicate for
     * "does this approach have a usable outgoing movement", because a partially-restricted
     * multimodal movement (car prohibited but bus legal) is still a legal approach for bus.
     */
    public boolean anyLegal() {
        return !legalModes().isEmpty();
    }

    /** True when no controlled mode is restricted (a free movement for every controlled mode). */
    public boolean fullyLegal() {
        return restrictedModes.isEmpty();
    }

    /** True when every controlled mode is restricted. */
    public boolean fullyRestricted() {
        return !controlledModes.isEmpty() && restrictedModes.containsAll(controlledModes);
    }

    /** Deterministic identifier for this movement (used for stable signal IO references). */
    public String movementId() {
        return incomingLinkId + "->" + outgoingLinkId + "|" + String.join(",", controlledModes);
    }
}
