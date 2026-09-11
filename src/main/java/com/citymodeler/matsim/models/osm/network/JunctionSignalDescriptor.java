package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * Network-side descriptor for one signalized junction (a single kept OSM node or a nearby cluster).
 * Pure data + queries; contains no MATSim or QSim types.
 */
public final class JunctionSignalDescriptor {

    private final String junctionId;
    private final String primaryOsmNodeId;
    private final List<String> osmNodeIds;
    private final boolean confirmedSignalized;
    private final int signalConfidence;
    private final String provenanceSummary;
    private final List<String> incomingLinks;
    private final List<String> outgoingLinks;
    private final List<SignalizedMovement> movements;

    public JunctionSignalDescriptor(
            String junctionId,
            String primaryOsmNodeId,
            List<String> osmNodeIds,
            boolean confirmedSignalized,
            int signalConfidence,
            String provenanceSummary,
            List<String> incomingLinks,
            List<String> outgoingLinks,
            List<SignalizedMovement> movements) {
        this.junctionId = Objects.requireNonNull(junctionId, "junctionId");
        this.primaryOsmNodeId = Objects.requireNonNull(primaryOsmNodeId, "primaryOsmNodeId");
        this.osmNodeIds = List.copyOf(new TreeSet<>(osmNodeIds));
        this.confirmedSignalized = confirmedSignalized;
        this.signalConfidence = signalConfidence;
        this.provenanceSummary = Objects.requireNonNull(provenanceSummary, "provenanceSummary");
        this.incomingLinks = List.copyOf(new TreeSet<>(incomingLinks));
        this.outgoingLinks = List.copyOf(new TreeSet<>(outgoingLinks));
        List<SignalizedMovement> sorted = new ArrayList<>(movements);
        sorted.sort((a, b) -> a.movementId().compareTo(b.movementId()));
        this.movements = List.copyOf(sorted);
    }

    public String junctionId() {
        return junctionId;
    }

    public String primaryOsmNodeId() {
        return primaryOsmNodeId;
    }

    public List<String> osmNodeIds() {
        return osmNodeIds;
    }

    public boolean confirmedSignalized() {
        return confirmedSignalized;
    }

    public int signalConfidence() {
        return signalConfidence;
    }

    public String provenanceSummary() {
        return provenanceSummary;
    }

    public List<String> incomingLinks() {
        return incomingLinks;
    }

    public List<String> outgoingLinks() {
        return outgoingLinks;
    }

    public List<SignalizedMovement> movements() {
        return movements;
    }

    /** Movements leaving the junction via the given incoming link (empty if none). */
    public List<SignalizedMovement> movementsForIncoming(String incomingLinkId) {
        List<SignalizedMovement> result = new ArrayList<>();
        for (SignalizedMovement m : movements) {
            if (m.incomingLinkId().equals(incomingLinkId)) {
                result.add(m);
            }
        }
        return result;
    }

    /** True when the junction has an enumerated movement from in to out. */
    public boolean hasMovement(String incomingLinkId, String outgoingLinkId) {
        for (SignalizedMovement m : movements) {
            if (m.incomingLinkId().equals(incomingLinkId) && m.outgoingLinkId().equals(outgoingLinkId)) {
                return true;
            }
        }
        return false;
    }

    public int legalMovementCount() {
        int c = 0;
        for (SignalizedMovement m : movements) {
            if (m.fullyLegal()) {
                c++;
            }
        }
        return c;
    }

    public int prohibitedMovementCount() {
        int c = 0;
        for (SignalizedMovement m : movements) {
            if (!m.fullyLegal()) {
                c++;
            }
        }
        return c;
    }

    /** Modes that can traverse the junction, across all enumerated movements. */
    public Set<String> controlledModeUnion() {
        Set<String> union = new TreeSet<>();
        for (SignalizedMovement m : movements) {
            union.addAll(m.controlledModes());
        }
        return union;
    }

    public String debugSummary() {
        StringJoiner j = new StringJoiner(",", "{", "}");
        for (String l : incomingLinks) {
            j.add("in:" + l);
        }
        return "Junction[" + junctionId + " nodes=" + osmNodeIds + " moves=" + movements.size() + "]";
    }
}
