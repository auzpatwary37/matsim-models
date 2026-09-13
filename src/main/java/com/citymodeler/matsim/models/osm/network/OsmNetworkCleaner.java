package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;

/**
 * Cleans a contracted network so that each configured routable mode can actually be routed: within
 * the directed subgraph of links permitting that mode, only the largest strongly connected component
 * is retained (every surviving link is reachable from and can return to every other). Links outside
 * the largest SCC for every routable mode are removed and quarantined (never silently deleted);
 * non-routable modes keep their sinks and sources.
 *
 * <p>Also drops structurally invalid links (zero/negative length, non-finite metrics, missing
 * endpoints, or empty mode sets), except artificial transit connectors whose id begins with
 * {@code pt_}, which a later phase may add.
 *
 * <p>Deterministic: node/link maps are traversed in insertion order and all internal accumulators are
 * sorted; identical input yields identical output. Mutates the network in place and calls
 * {@code postProcess()}.
 */
public final class OsmNetworkCleaner {

    /** Artificial transit connector id prefix exempt from zero-length and component removal. */
    static final String ARTIFICIAL_LINK_PREFIX = "pt_";

    private OsmNetworkCleaner() {
    }

    /**
     * @param routableModes modes for which strongly connected routability must hold; each is cleaned
     *                      independently to its largest SCC. Modes not listed keep sinks/sources.
     */
    public static CleanResult clean(Network network, Set<String> routableModes) {
        return clean(network, routableModes, linkId -> List.of());
    }

    /**
     * @param sourceWayResolver maps a network link id to its source OSM way ids, so quarantined
     *                           components can report the source OSM ids they were built from.
     */
    public static CleanResult clean(Network network, Set<String> routableModes,
                                    java.util.function.Function<String, List<String>> sourceWayResolver) {
        List<OsmImportIssue> issues = new ArrayList<>();
        Set<String> normalizedRoutable = new TreeSet<>(routableModes);

        // Step 1: structurally invalid links.
        Set<String> invalid = new TreeSet<>();
        for (Map.Entry<Id<Link>, Link> entry : network.getLinks().entrySet()) {
            if (isInvalid(network, entry.getValue())) {
                invalid.add(entry.getKey().toString());
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "removed-invalid-link",
                        "Removed link " + entry.getKey() + " (invalid geometry, metrics, or endpoints)",
                        null));
            }
        }
        removeLinks(network, invalid);

        // Step 2: per routable mode, keep only links in the largest SCC.
        Set<String> everRoutable = new TreeSet<>();
        Set<String> kept = new TreeSet<>();
        for (String mode : normalizedRoutable) {
            Set<String> modeLinks = linksPermittingMode(network, mode);
            if (modeLinks.isEmpty()) {
                continue;
            }
            everRoutable.addAll(modeLinks);
            kept.addAll(largestStronglyConnectedComponent(network, modeLinks));
        }

        Set<String> toRemove = new TreeSet<>(everRoutable);
        toRemove.removeAll(kept);

        // Step 3: quarantine removed links as coherent undirected components.
        List<List<String>> quarantinedGroups = quarantineComponents(network, toRemove);
        for (List<String> component : quarantinedGroups) {
            Set<String> sourceWayIds = new TreeSet<>();
            for (String linkId : component) {
                sourceWayIds.addAll(sourceWayResolver.apply(linkId));
            }
            String sourceSuffix = sourceWayIds.isEmpty() ? ""
                    : "; source OSM ways: " + String.join(",", sourceWayIds);
            issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "quarantined-component",
                    "Quarantined disconnected component of " + component.size()
                            + " link(s): " + String.join(",", component) + sourceSuffix, null));
        }
        removeLinks(network, toRemove);

        // Step 4: orphaned nodes.
        Set<String> nodesWithLinks = new TreeSet<>();
        for (Link link : network.getLinks().values()) {
            nodesWithLinks.add(link.getFromNodeId().toString());
            nodesWithLinks.add(link.getToNodeId().toString());
        }
        Set<Id<Node>> nodesToRemove = new java.util.LinkedHashSet<>();
        for (Id<Node> nodeId : network.getNodes().keySet()) {
            if (!nodesWithLinks.contains(nodeId.toString())) {
                nodesToRemove.add(nodeId);
            }
        }
        for (Id<Node> nodeId : new java.util.ArrayList<>(nodesToRemove)) {
            network.removeNode(nodeId);
        }

        network.postProcess();

        return new CleanResult(toRemove.size(), nodesToRemove.size(), List.copyOf(issues),
                List.copyOf(quarantinedGroups));
    }

    private static void removeLinks(Network network, Set<String> linkIds) {
        for (String linkId : linkIds) {
            network.removeLink(Id.create(linkId, Link.class));
        }
    }

    private static boolean isInvalid(Network network, Link link) {
        if (link.getId().toString().startsWith(ARTIFICIAL_LINK_PREFIX)) {
            return false;
        }
        if (!network.getNodes().containsKey(link.getFromNodeId())
                || !network.getNodes().containsKey(link.getToNodeId())) {
            return true;
        }
        if (link.getAllowedModes().isEmpty()) {
            return true;
        }
        return !(link.getLength() > 0.0)
                || !Double.isFinite(link.getLength())
                || !Double.isFinite(link.getFreespeed())
                || !Double.isFinite(link.getCapacity())
                || !Double.isFinite(link.getNumberOfLanes());
    }

    private static Set<String> linksPermittingMode(Network network, String mode) {
        Set<String> ids = new TreeSet<>();
        for (Map.Entry<Id<Link>, Link> entry : network.getLinks().entrySet()) {
            if (entry.getValue().getAllowedModes().contains(mode)) {
                ids.add(entry.getKey().toString());
            }
        }
        return ids;
    }

    /**
     * Largest strongly connected component (by link count) of the directed subgraph induced by
     * {@code linkIds}, via iterative Kosaraju over the induced NODE graph. Deterministic: adjacency
     * and seeds are sorted.
     */
    static Set<String> largestStronglyConnectedComponent(Network network, Set<String> linkIds) {
        // Induced node adjacency (from-node -> sorted to-nodes) and reverse.
        Map<String, Set<String>> out = new TreeMap<>();
        Map<String, Set<String>> rev = new TreeMap<>();
        for (String linkId : linkIds) {
            Link link = network.getLinks().get(Id.create(linkId, Link.class));
            if (link == null) {
                continue;
            }
            String from = link.getFromNodeId().toString();
            String to = link.getToNodeId().toString();
            out.computeIfAbsent(from, k -> new TreeSet<>()).add(to);
            out.computeIfAbsent(to, k -> new TreeSet<>());
            rev.computeIfAbsent(to, k -> new TreeSet<>()).add(from);
            rev.computeIfAbsent(from, k -> new TreeSet<>());
        }

        // Pass 1: finishing order over the forward graph.
        Set<String> visited = new TreeSet<>();
        Deque<String> order = new ArrayDeque<>();
        for (String node : out.keySet()) {
            if (!visited.contains(node)) {
                dfsFinishOrder(node, out, visited, order);
            }
        }

        // Pass 2: assign SCCs over the reverse graph in decreasing finish order.
        Set<String> assigned = new TreeSet<>();
        List<Set<String>> components = new ArrayList<>();
        while (!order.isEmpty()) {
            String node = order.pop();
            if (!assigned.add(node)) {
                continue;
            }
            Set<String> component = new TreeSet<>();
            Deque<String> stack = new ArrayDeque<>();
            stack.push(node);
            while (!stack.isEmpty()) {
                String current = stack.pop();
                component.add(current);
                for (String prev : rev.getOrDefault(current, Set.of())) {
                    if (assigned.add(prev)) {
                        stack.push(prev);
                    }
                }
            }
            components.add(component);
        }

        // Largest component by member link count.
        Set<String> best = new TreeSet<>();
        int bestCount = -1;
        for (Set<String> component : components) {
            Set<String> componentLinks = new TreeSet<>();
            for (String linkId : linkIds) {
                Link link = network.getLinks().get(Id.create(linkId, Link.class));
                if (link == null) {
                    continue;
                }
                if (component.contains(link.getFromNodeId().toString())
                        && component.contains(link.getToNodeId().toString())) {
                    componentLinks.add(linkId);
                }
            }
            if (componentLinks.size() > bestCount) {
                bestCount = componentLinks.size();
                best = componentLinks;
            }
        }
        return best;
    }

    private static void dfsFinishOrder(String start, Map<String, Set<String>> out,
                                       Set<String> visited, Deque<String> order) {
        Deque<String> nodeStack = new ArrayDeque<>();
        Deque<List<String>> adjStack = new ArrayDeque<>();
        Deque<Integer> indexStack = new ArrayDeque<>();

        visited.add(start);
        nodeStack.push(start);
        adjStack.push(new ArrayList<>(out.getOrDefault(start, Set.of())));
        indexStack.push(0);

        while (!nodeStack.isEmpty()) {
            String current = nodeStack.peek();
            List<String> adjacency = adjStack.peek();
            int index = indexStack.pop();
            boolean descended = false;
            while (index < adjacency.size()) {
                String next = adjacency.get(index);
                index++;
                if (visited.add(next)) {
                    indexStack.push(index);
                    nodeStack.push(next);
                    adjStack.push(new ArrayList<>(out.getOrDefault(next, Set.of())));
                    indexStack.push(0);
                    descended = true;
                    break;
                }
            }
            if (!descended) {
                nodeStack.pop();
                adjStack.pop();
                order.push(current);
            }
        }
    }

    private static List<List<String>> quarantineComponents(Network network, Set<String> toRemove) {
        // Node -> incident removed-link ids, built once.
        Map<String, Set<String>> nodeLinks = new TreeMap<>();
        for (String linkId : toRemove) {
            Link link = network.getLinks().get(Id.create(linkId, Link.class));
            if (link == null) {
                continue;
            }
            nodeLinks.computeIfAbsent(link.getFromNodeId().toString(), k -> new TreeSet<>()).add(linkId);
            nodeLinks.computeIfAbsent(link.getToNodeId().toString(), k -> new TreeSet<>()).add(linkId);
        }

        List<List<String>> groups = new ArrayList<>();
        Set<String> assigned = new TreeSet<>();
        for (String seed : toRemove) {
            if (!assigned.add(seed)) {
                continue;
            }
            List<String> component = new ArrayList<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                String current = queue.poll();
                component.add(current);
                Link link = network.getLinks().get(Id.create(current, Link.class));
                if (link == null) {
                    continue;
                }
                for (String nodeId : List.of(link.getFromNodeId().toString(),
                        link.getToNodeId().toString())) {
                    for (String neighbor : nodeLinks.getOrDefault(nodeId, Set.of())) {
                        if (assigned.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
            component.sort(null);
            groups.add(List.copyOf(component));
        }
        return groups;
    }

    public record CleanResult(int removedLinks, int removedNodes, List<OsmImportIssue> issues,
                              List<List<String>> quarantinedComponents) {
    }
}
