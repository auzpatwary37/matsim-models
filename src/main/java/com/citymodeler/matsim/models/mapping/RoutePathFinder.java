package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.turnrestrictions.TurnRestrictionIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Finds least-cost paths between links on the network, respecting turn restrictions.
 */
public final class RoutePathFinder {

    private final Network network;
    private final TurnRestrictionIndex turnRestrictions;
    private final Set<Id<Link>> excludedLinks;
    private final double maxDistance;

    public RoutePathFinder(Network network, TurnRestrictionIndex turnRestrictions,
                           Set<Id<Link>> excludedLinks, double maxDistance) {
        this.network = network;
        this.turnRestrictions = turnRestrictions;
        this.excludedLinks = excludedLinks != null ? excludedLinks : Set.of();
        this.maxDistance = maxDistance;
    }

    public List<Id<Link>> findPath(Id<Link> fromLink, Id<Link> toLink) {
        if (fromLink.equals(toLink)) return List.of(fromLink);

        Link from = network.getLinks().get(fromLink);
        Link to = network.getLinks().get(toLink);
        if (from == null || to == null) return null;

        // State: (node, incoming link) — the incoming link tells us what turn we're making
        Map<StateKey, Double> bestDist = new HashMap<>();
        Map<StateKey, StateKey> parent = new HashMap<>();
        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        // pq entries: [distance, stateIndex]
        Map<StateKey, Integer> stateIndex = new HashMap<>();
        List<StateKey> states = new ArrayList<>();

        StateKey start = new StateKey(from.getToNodeId(), fromLink);
        int startIdx = registerState(states, stateIndex, start);
        bestDist.put(start, 0.0);
        pq.add(new double[]{0.0, startIdx});

        while (!pq.isEmpty()) {
            double[] entry = pq.poll();
            double d = entry[0];
            StateKey curr = states.get((int) entry[1]);
            if (d > bestDist.getOrDefault(curr, Double.MAX_VALUE)) continue;
            if (d > maxDistance) break;

            Node node = network.getNodes().get(curr.node());
            if (node == null) continue;

            for (Link next : node.getOutLinks().values()) {
                if (excludedLinks.contains(next.getId())) continue;

                if (turnRestrictions != null && curr.lastLink() != null) {
                    String mode = dominantMode(next);
                    if (isDisallowed(node, curr.lastLink(), next.getId(), mode)) continue;
                }

                double edgeCost = next.getLength() / (next.getFreespeed() > 0 ? next.getFreespeed() : 13.9);
                StateKey nextKey = new StateKey(next.getToNodeId(), next.getId());
                int nextIdx = registerState(states, stateIndex, nextKey);
                double newDist = d + edgeCost;

                if (newDist < bestDist.getOrDefault(nextKey, Double.MAX_VALUE)) {
                    bestDist.put(nextKey, newDist);
                    parent.put(nextKey, curr);
                    pq.add(new double[]{newDist, nextIdx});

                    // Reached goal?
                    if (next.getId().equals(toLink)) {
                        return reconstruct(parent, nextKey);
                    }
                    // Next link's toNode is toLink's fromNode → prepend toLink
                    if (next.getToNodeId().equals(to.getFromNodeId())) {
                        List<Id<Link>> path = reconstruct(parent, nextKey);
                        path.add(toLink);
                        return path;
                    }
                }
            }
        }
        return null;
    }

    private List<Id<Link>> reconstruct(Map<StateKey, StateKey> parent, StateKey end) {
        List<Id<Link>> path = new ArrayList<>();
        StateKey curr = end;
        while (curr != null) {
            path.add(curr.lastLink());
            curr = parent.get(curr);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private int registerState(List<StateKey> states, Map<StateKey, Integer> idx, StateKey key) {
        return idx.computeIfAbsent(key, k -> {
            states.add(k);
            return states.size() - 1;
        });
    }

    private String dominantMode(Link link) {
        if (link.getAllowedModes() != null && !link.getAllowedModes().isEmpty()) {
            return link.getAllowedModes().iterator().next();
        }
        return "car";
    }

    private boolean isDisallowed(Node node, Id<Link> incoming, Id<Link> outgoing, String mode) {
        return turnRestrictions.isDisallowed(mode, incoming.toString(), outgoing.toString());
    }

    record StateKey(Id<Node> node, Id<Link> lastLink) {
    }
}
