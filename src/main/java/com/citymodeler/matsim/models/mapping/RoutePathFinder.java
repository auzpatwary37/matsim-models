package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Finds least-cost paths between links on the network, respecting turn restrictions.
 *
 * <p>Review fixes:
 * <ul>
 *   <li>#3 Cost is accumulated as <b>distance</b> (sum of link lengths) and compared against a
 *       distance cap ({@code maxDistance}), so the termination bound and the edge metric share
 *       units. The old code accumulated travel time but compared it to a distance parameter.</li>
 *   <li>#4 The destination link is entered through the same state expansion as every other link,
 *       so the final turn into it is validated against the restrictions (there is no separate
 *       early-return that bypassed the check).</li>
 *   <li>#5 The search state carries a window of recent link ids, not just the immediately preceding
 *       link, so multi-link restriction sequences are represented and enforced. Restrictions are read
 *       from each link's {@code disallowedNextLinks} attribute (the Phase-1 multi-link model).</li>
 *   <li>#6 The route's transport mode is passed explicitly and used for restriction evaluation,
 *       instead of picking an arbitrary mode from a link's allowed-mode set.</li>
 *   <li>Mode access is enforced on every traversed link (shared policy with
 *       {@link StopCandidateScorer#modeCompatible}), so a bus/PT route cannot route through a
 *       car-only intermediate link.</li>
 * </ul>
 */
public final class RoutePathFinder {

    private final Network network;
    private final Set<Id<Link>> excludedLinks;
    private final double maxDistance;
    private final String routeMode;
    /** from-link id -> disallowed next-link sequences that apply to this route mode. */
    private final Map<String, List<List<String>>> disallowed;
    private final int maxSeqLen;
    private final int windowCap;

    public RoutePathFinder(Network network, Set<Id<Link>> excludedLinks,
                           double maxDistance, String routeMode) {
        this.network = network;
        this.excludedLinks = excludedLinks != null ? excludedLinks : Set.of();
        this.maxDistance = maxDistance;
        this.routeMode = (routeMode == null || routeMode.isBlank()) ? "car" : routeMode;

        // A generic "pt" route honors any transit-mode restriction; a specific mode honors its own.
        Set<String> checkedModes = new LinkedHashSet<>();
        if ("pt".equals(routeMode)) {
            checkedModes.addAll(StopCandidateScorer.TRANSIT_MODES);
        } else {
            checkedModes.add(routeMode);
        }

        Map<String, List<List<String>>> map = new HashMap<>();
        int maxLen = 1;
        for (Link link : network.getLinks().values()) {
            Object json = link.getAttributes().getAttribute("disallowedNextLinks");
            if (json == null) {
                continue;
            }
            Map<String, List<List<String>>> byMode;
            try {
                byMode = DisallowedNextLinks.fromJson(String.valueOf(json)).asMap();
            } catch (RuntimeException e) {
                continue;
            }
            List<List<String>> sequences = new ArrayList<>();
            for (String mode : checkedModes) {
                List<List<String>> seqs = byMode.get(mode);
                if (seqs != null) {
                    sequences.addAll(seqs);
                }
            }
            if (!sequences.isEmpty()) {
                map.put(link.getId().toString(), sequences);
                for (List<String> s : sequences) {
                    maxLen = Math.max(maxLen, s.size());
                }
            }
        }
        this.disallowed = map;
        this.maxSeqLen = Math.min(maxLen, 4);
        // Anchor + up to maxSeqLen following links.
        this.windowCap = maxSeqLen + 1;
    }

    public List<Id<Link>> findPath(Id<Link> fromLink, Id<Link> toLink) {
        if (fromLink.equals(toLink)) {
            return List.of(fromLink);
        }
        Link from = network.getLinks().get(fromLink);
        Link to = network.getLinks().get(toLink);
        if (from == null || to == null) {
            return null;
        }

        Map<StateKey, Double> best = new HashMap<>();
        Map<StateKey, StateKey> parent = new HashMap<>();
        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        List<StateKey> states = new ArrayList<>();
        Map<StateKey, Integer> stateIndex = new HashMap<>();

        StateKey start = new StateKey(from.getToNodeId(), List.of(fromLink.toString()));
        int startIdx = register(stateIndex, states, start);
        best.put(start, 0.0);
        pq.add(new double[]{0.0, startIdx});

        while (!pq.isEmpty()) {
            double[] entry = pq.poll();
            double d = entry[0];
            StateKey curr = states.get((int) entry[1]);
            if (d > best.getOrDefault(curr, Double.MAX_VALUE)) {
                continue;
            }
            if (d > maxDistance) {
                break;
            }
            Node node = network.getNodes().get(curr.node());
            if (node == null) {
                continue;
            }
            for (Link next : node.getOutLinks().values()) {
                if (excludedLinks.contains(next.getId())) {
                    continue;
                }
                // Mode access is enforced on every traversed link, not only at stop-candidate
                // selection: a bus/PT route must not route through a car-only intermediate link.
                if (!StopCandidateScorer.modeCompatible(next.getAllowedModes(), routeMode)) {
                    continue;
                }
                String nextId = next.getId().toString();
                List<String> window = append(curr.window(), nextId);
                if (isForbidden(window)) {
                    continue; // review #4/#5: turn (including the final one) is restricted
                }
                double newDist = d + next.getLength();
                if (newDist > maxDistance) {
                    continue;
                }
                StateKey nextKey = new StateKey(next.getToNodeId(), window);
                double prev = best.getOrDefault(nextKey, Double.MAX_VALUE);
                if (newDist < prev) {
                    best.put(nextKey, newDist);
                    parent.put(nextKey, curr);
                    int nextIdx = register(stateIndex, states, nextKey);
                    pq.add(new double[]{newDist, nextIdx});
                    if (next.getId().equals(toLink)) {
                        return reconstruct(parent, nextKey);
                    }
                }
            }
        }
        return null;
    }

    private List<String> append(List<String> window, String nextId) {
        List<String> w = new ArrayList<>(window.size() + 1);
        w.addAll(window);
        w.add(nextId);
        if (w.size() > windowCap) {
            w.subList(0, w.size() - windowCap).clear();
        }
        return w;
    }

    /** True if appending the last link completes any disallowed sequence anchored further back. */
    private boolean isForbidden(List<String> window) {
        int len = window.size();
        for (int k = 1; k <= maxSeqLen; k++) {
            // The forbidden run is [anchor, S0, ..., S(k-1)] = k+1 links ending at the last element,
            // so the anchor sits k+1 positions from the end.
            int anchor = len - 1 - k;
            if (anchor < 0) {
                continue;
            }
            List<List<String>> seqs = disallowed.get(window.get(anchor));
            if (seqs == null) {
                continue;
            }
            for (List<String> s : seqs) {
                if (s.size() != k) {
                    continue;
                }
                boolean match = true;
                for (int j = 0; j < k; j++) {
                    if (!window.get(anchor + 1 + j).equals(s.get(j))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return true;
                }
            }
        }
        return false;
    }

    private int register(Map<StateKey, Integer> idx, List<StateKey> states, StateKey key) {
        return idx.computeIfAbsent(key, k -> {
            states.add(k);
            return states.size() - 1;
        });
    }

    private List<Id<Link>> reconstruct(Map<StateKey, StateKey> parent, StateKey end) {
        List<String> ids = new ArrayList<>();
        StateKey c = end;
        while (c != null) {
            List<String> w = c.window();
            String last = w.get(w.size() - 1);
            if (ids.isEmpty() || !ids.get(0).equals(last)) {
                ids.add(last);
            }
            c = parent.get(c);
        }
        java.util.Collections.reverse(ids);
        List<Id<Link>> path = new ArrayList<>(ids.size());
        for (String id : ids) {
            path.add(Id.create(id, Link.class));
        }
        return path;
    }

    private record StateKey(Id<Node> node, List<String> window) {
        StateKey {
            window = List.copyOf(window);
        }
    }
}
