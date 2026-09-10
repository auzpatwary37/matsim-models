package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;

/**
 * Cleans the network: removes orphaned nodes, dangling links, and
 * optionally disconnected components that lack transit-relevant modes.
 */
public final class OsmNetworkCleaner {

    private OsmNetworkCleaner() {
    }

    public static CleanResult clean(Network network, boolean removeDisconnectedNonTransit) {
        List<OsmImportIssue> issues = new ArrayList<>();
        Set<String> linksToRemove = new HashSet<>();
        Set<String> nodesToRemove = new HashSet<>();

        if (removeDisconnectedNonTransit) {
            // Find connected components
            Map<com.citymodeler.matsim.models.api.Id<Link>, Boolean> visited = new java.util.HashMap<>();
            for (var entry : network.getLinks().entrySet()) {
                if (visited.containsKey(entry.getKey())) continue;
                Set<com.citymodeler.matsim.models.api.Id<Link>> component = new HashSet<>();
                boolean hasTransit = false;
                // BFS
                var queue = new java.util.ArrayDeque<com.citymodeler.matsim.models.api.Id<Link>>();
                queue.add(entry.getKey());
                visited.put(entry.getKey(), true);
                while (!queue.isEmpty()) {
                    var linkId = queue.poll();
                    Link link = network.getLinks().get(linkId);
                    if (link == null) continue;
                    component.add(linkId);
                    if (link.getAllowedModes().stream().anyMatch(m ->
                            m.equals("bus") || m.equals("pt") || m.equals("rail")
                                    || m.equals("tram") || m.equals("subway"))) {
                        hasTransit = true;
                    }
                    // Neighbors: links sharing a node
                    String fromNode = link.getFromNode().getId().toString();
                    String toNode = link.getToNode().getId().toString();
                    for (var other : network.getLinks().entrySet()) {
                        if (visited.containsKey(other.getKey())) continue;
                        Link ol = other.getValue();
                        if (ol.getFromNode().getId().toString().equals(toNode)
                                || ol.getToNode().getId().toString().equals(toNode)
                                || ol.getFromNode().getId().toString().equals(fromNode)
                                || ol.getToNode().getId().toString().equals(fromNode)) {
                            visited.put(other.getKey(), true);
                            queue.add(other.getKey());
                        }
                    }
                }
                if (!hasTransit && component.size() < 3) {
                    for (var linkId : component) {
                        linksToRemove.add(linkId.toString());
                    }
                    issues.add(new OsmImportIssue(OsmIssueSeverity.INFO,
                            "removed-small-nontransit-component",
                            "Removed " + component.size() + "-link non-transit component", null));
                }
            }
        }

        // Remove orphaned nodes (no links after removal)
        if (!linksToRemove.isEmpty()) {
            Set<String> nodesWithLinks = new HashSet<>();
            for (var entry : network.getLinks().entrySet()) {
                if (linksToRemove.contains(entry.getKey().toString())) continue;
                nodesWithLinks.add(entry.getValue().getFromNode().getId().toString());
                nodesWithLinks.add(entry.getValue().getToNode().getId().toString());
            }
            for (var entry : network.getNodes().entrySet()) {
                if (!nodesWithLinks.contains(entry.getKey().toString())) {
                    nodesToRemove.add(entry.getKey().toString());
                }
            }
        }

        int removedLinks = linksToRemove.size();
        int removedNodes = nodesToRemove.size();
        return new CleanResult(removedLinks, removedNodes, issues);
    }

    public record CleanResult(int removedLinks, int removedNodes, List<OsmImportIssue> issues) {
    }
}
