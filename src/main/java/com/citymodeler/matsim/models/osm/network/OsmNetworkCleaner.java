package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;

/**
 * Cleans the network in-place: removes orphaned nodes, dangling links, and
 * optionally small disconnected components that lack transit-relevant modes.
 * Calls {@code network.postProcess()} after mutations.
 */
public final class OsmNetworkCleaner {

    private OsmNetworkCleaner() {
    }

    public static CleanResult clean(Network network, boolean removeDisconnectedNonTransit) {
        List<OsmImportIssue> issues = new ArrayList<>();
        Set<Id<Link>> linksToRemove = new HashSet<>();

        if (removeDisconnectedNonTransit) {
            Map<Id<Link>, Boolean> visited = new HashMap<>();
            // Build adjacency by node for efficient BFS
            Map<String, List<Id<Link>>> nodeToLinks = new HashMap<>();
            for (var entry : network.getLinks().entrySet()) {
                Link link = entry.getValue();
                nodeToLinks.computeIfAbsent(link.getFromNode().getId().toString(), k -> new ArrayList<>()).add(entry.getKey());
                nodeToLinks.computeIfAbsent(link.getToNode().getId().toString(), k -> new ArrayList<>()).add(entry.getKey());
            }

            for (var entry : network.getLinks().entrySet()) {
                if (visited.containsKey(entry.getKey())) continue;
                Set<Id<Link>> component = new HashSet<>();
                boolean hasTransit = false;

                var queue = new ArrayDeque<Id<Link>>();
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
                    String fromNode = link.getFromNode().getId().toString();
                    String toNode = link.getToNode().getId().toString();
                    for (String nodeId : Set.of(fromNode, toNode)) {
                        for (Id<Link> neighborId : nodeToLinks.getOrDefault(nodeId, List.of())) {
                            if (!visited.containsKey(neighborId)) {
                                visited.put(neighborId, true);
                                queue.add(neighborId);
                            }
                        }
                    }
                }

                if (!hasTransit && component.size() < 3) {
                    linksToRemove.addAll(component);
                    issues.add(new OsmImportIssue(OsmIssueSeverity.INFO,
                            "removed-small-nontransit-component",
                            "Removed " + component.size() + "-link non-transit component", null));
                }
            }
        }

        // Remove links
        for (Id<Link> linkId : linksToRemove) {
            network.removeLink(linkId);
        }

        // Remove orphaned nodes (no remaining links reference them)
        Set<String> nodesWithLinks = new HashSet<>();
        for (Link link : network.getLinks().values()) {
            nodesWithLinks.add(link.getFromNode().getId().toString());
            nodesWithLinks.add(link.getToNode().getId().toString());
        }

        Set<Id<Node>> nodesToRemove = new HashSet<>();
        for (var entry : network.getNodes().entrySet()) {
            if (!nodesWithLinks.contains(entry.getKey().toString())) {
                nodesToRemove.add(entry.getKey());
            }
        }
        for (Id<Node> nodeId : nodesToRemove) {
            network.removeNode(nodeId);
        }

        network.postProcess();

        return new CleanResult(linksToRemove.size(), nodesToRemove.size(), List.copyOf(issues));
    }

    public record CleanResult(int removedLinks, int removedNodes, List<OsmImportIssue> issues) {
    }
}
