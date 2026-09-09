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

    public OsmNetworkBuildResult(
            Network cleanedNetwork,
            List<OsmImportIssue> issues,
            Map<String, OsmLinkRef> linkRefsByLinkId,
            Map<String, List<String>> linkIdsByOsmWayId) {
        this.cleanedNetwork = Objects.requireNonNull(cleanedNetwork, "cleanedNetwork");
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        this.linkRefsByLinkId = Collections.unmodifiableSortedMap(new TreeMap<>(
                Objects.requireNonNull(linkRefsByLinkId, "linkRefsByLinkId")));
        this.linkIdsByOsmWayId = Collections.unmodifiableSortedMap(new TreeMap<>(
                Objects.requireNonNull(linkIdsByOsmWayId, "linkIdsByOsmWayId")));
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
}
