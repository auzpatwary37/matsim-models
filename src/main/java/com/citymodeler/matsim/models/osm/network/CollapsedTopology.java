package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;

/** Result of topology contraction: compact network + provenance + geometry + classification. */
public record CollapsedTopology(
        Network network,
        Map<String, OsmCollapsedLink> collapsedLinksByLinkId,
        Map<String, List<String>> linkIdsByOsmWayId,
        Map<String, OsmPolyline> geometry,
        Map<String, OsmNodeClassification> classification,
        Set<String> routingNodeIds,
        List<OsmImportIssue> issues) {
}
