package com.citymodeler.matsim.models.osm.network;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks;
import com.citymodeler.matsim.models.network.turnrestrictions.TurnRestrictionIndex;
import com.citymodeler.matsim.models.osm.OsmImportIssue;

/** Result of signal-aware simplification: a collapsed network plus signal-ready metadata. */
public final class OsmSimplifiedNetwork {

    private final Network network;
    private final OsmGeometryStore geometryStore;
    private final Map<String, OsmCollapsedLink> collapsedLinksByLinkId;
    private final Map<String, List<String>> linkIdsByOsmWayId;
    private final List<JunctionSignalDescriptor> signalizedJunctions;
    private final Map<String, JunctionSignalDescriptor> junctionsByNodeId;
    private final TurnRestrictionIndex turnRestrictionIndex;
    private final Map<String, DisallowedNextLinks> perLinkDisallowed;
    private final SignalReadinessReport report;
    private final List<OsmImportIssue> issues;

    public OsmSimplifiedNetwork(
            Network network,
            OsmGeometryStore geometryStore,
            Map<String, OsmCollapsedLink> collapsedLinksByLinkId,
            Map<String, List<String>> linkIdsByOsmWayId,
            List<JunctionSignalDescriptor> signalizedJunctions,
            Map<String, JunctionSignalDescriptor> junctionsByNodeId,
            TurnRestrictionIndex turnRestrictionIndex,
            Map<String, DisallowedNextLinks> perLinkDisallowed,
            SignalReadinessReport report,
            List<OsmImportIssue> issues) {
        this.network = Objects.requireNonNull(network, "network");
        this.geometryStore = Objects.requireNonNull(geometryStore, "geometryStore");
        this.collapsedLinksByLinkId = Collections.unmodifiableMap(new TreeMap<>(collapsedLinksByLinkId));
        this.linkIdsByOsmWayId = Collections.unmodifiableMap(new TreeMap<>(linkIdsByOsmWayId));
        this.signalizedJunctions = List.copyOf(signalizedJunctions);
        this.junctionsByNodeId = Collections.unmodifiableMap(new TreeMap<>(junctionsByNodeId));
        this.turnRestrictionIndex = turnRestrictionIndex;
        this.perLinkDisallowed = Collections.unmodifiableMap(new TreeMap<>(perLinkDisallowed));
        this.report = Objects.requireNonNull(report, "report");
        this.issues = List.copyOf(issues);
    }

    public Network network() {
        return network;
    }

    public OsmGeometryStore geometryStore() {
        return geometryStore;
    }

    public Map<String, OsmCollapsedLink> collapsedLinksByLinkId() {
        return collapsedLinksByLinkId;
    }

    public Map<String, List<String>> linkIdsByOsmWayId() {
        return linkIdsByOsmWayId;
    }

    public List<JunctionSignalDescriptor> signalizedJunctions() {
        return signalizedJunctions;
    }

    public Map<String, JunctionSignalDescriptor> junctionsByNodeId() {
        return junctionsByNodeId;
    }

    public TurnRestrictionIndex turnRestrictionIndex() {
        return turnRestrictionIndex;
    }

    public Map<String, DisallowedNextLinks> perLinkDisallowed() {
        return perLinkDisallowed;
    }

    public SignalReadinessReport report() {
        return report;
    }

    public List<OsmImportIssue> issues() {
        return issues;
    }

    public OsmCollapsedLink collapsedLink(String linkId) {
        return collapsedLinksByLinkId.get(linkId);
    }

    /** The signalized junction anchored at the given network node id, or null. */
    public JunctionSignalDescriptor junctionAt(String networkNodeId) {
        return junctionsByNodeId.get(networkNodeId);
    }
}
