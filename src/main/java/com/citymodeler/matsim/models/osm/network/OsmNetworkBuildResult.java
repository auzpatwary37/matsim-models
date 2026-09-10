package com.citymodeler.matsim.models.osm.network;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;

public final class OsmNetworkBuildResult {
    private final Network cleanedNetwork;
    private final List<OsmImportIssue> issues;
    private final Map<String, OsmLinkRef> linkRefsByLinkId;
    private final Map<String, List<String>> linkIdsByOsmWayId;
    private final List<OsmStopHint> stopHints;
    private final Map<String, OsmLaneHint> laneHintsByLinkId;
    private final Map<String, OsmIntersectionLaneHint> intersectionLaneHintsByNodeId;
    private final OsmGeometryStore geometryStore;

    public OsmNetworkBuildResult(
            Network cleanedNetwork,
            List<OsmImportIssue> issues,
            Map<String, OsmLinkRef> linkRefsByLinkId,
            Map<String, List<String>> linkIdsByOsmWayId) {
        this(cleanedNetwork, issues, linkRefsByLinkId, linkIdsByOsmWayId,
                List.of(), Map.of(), Map.of(), OsmGeometryStore.empty());
    }

    public OsmNetworkBuildResult(
            Network cleanedNetwork,
            List<OsmImportIssue> issues,
            Map<String, OsmLinkRef> linkRefsByLinkId,
            Map<String, List<String>> linkIdsByOsmWayId,
            List<OsmStopHint> stopHints,
            Map<String, OsmLaneHint> laneHintsByLinkId,
            Map<String, OsmIntersectionLaneHint> intersectionLaneHintsByNodeId) {
        this(cleanedNetwork, issues, linkRefsByLinkId, linkIdsByOsmWayId,
                stopHints, laneHintsByLinkId, intersectionLaneHintsByNodeId, OsmGeometryStore.empty());
    }

    public OsmNetworkBuildResult(
            Network cleanedNetwork,
            List<OsmImportIssue> issues,
            Map<String, OsmLinkRef> linkRefsByLinkId,
            Map<String, List<String>> linkIdsByOsmWayId,
            List<OsmStopHint> stopHints,
            Map<String, OsmLaneHint> laneHintsByLinkId,
            Map<String, OsmIntersectionLaneHint> intersectionLaneHintsByNodeId,
            OsmGeometryStore geometryStore) {
        this.cleanedNetwork = Objects.requireNonNull(cleanedNetwork, "cleanedNetwork");
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        this.linkRefsByLinkId = Collections.unmodifiableSortedMap(new TreeMap<>(
                Objects.requireNonNull(linkRefsByLinkId, "linkRefsByLinkId")));
        this.linkIdsByOsmWayId = Collections.unmodifiableSortedMap(new TreeMap<>(
                Objects.requireNonNull(linkIdsByOsmWayId, "linkIdsByOsmWayId")));
        this.stopHints = List.copyOf(Objects.requireNonNull(stopHints, "stopHints"));
        this.laneHintsByLinkId = Collections.unmodifiableMap(new TreeMap<>(
                Objects.requireNonNull(laneHintsByLinkId, "laneHintsByLinkId")));
        this.intersectionLaneHintsByNodeId = Collections.unmodifiableMap(new TreeMap<>(
                Objects.requireNonNull(intersectionLaneHintsByNodeId, "intersectionLaneHintsByNodeId")));
        this.geometryStore = Objects.requireNonNull(geometryStore, "geometryStore");
    }

    public Network cleanedNetwork() {
        return cleanedNetwork;
    }

    public List<OsmImportIssue> issues() {
        return issues;
    }

    public Map<String, OsmLinkRef> linkRefsByLinkId() {
        return linkRefsByLinkId;
    }

    public Map<String, List<String>> linkIdsByOsmWayId() {
        return linkIdsByOsmWayId;
    }

    public List<OsmStopHint> stopHints() {
        return stopHints;
    }

    public Map<String, OsmLaneHint> laneHintsByLinkId() {
        return laneHintsByLinkId;
    }

    public Map<String, OsmIntersectionLaneHint> intersectionLaneHintsByNodeId() {
        return intersectionLaneHintsByNodeId;
    }

    public OsmGeometryStore geometryStore() {
        return geometryStore;
    }
}
