package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.turnrestrictions.TurnRestrictionIndex;
import com.citymodeler.matsim.models.osm.OsmElementType;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationMemberRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Signal-aware network simplifier. Collapses OSM geometry-only nodes into longer merged links while
 * preserving every node that carries routing, lane, signal, access, transit, or turn-restriction
 * significance. Produces a collapsed {@link Network}, link geometry, signalized-junction metadata,
 * re-attached turn restrictions, and a signal-readiness diagnostic report.
 *
 * <p>Deterministic: all collections are traversed in sorted order or built in deterministic order.
 */
public final class OsmSignalAwareSimplifier {

    private OsmSignalAwareSimplifier() {
    }

    public static OsmSimplifiedNetwork simplify(
            OsmNetworkBuildResult materialized,
            OsmImportResult importResult,
            OsmNetworkBuildConfig config,
            OsmSimplifyOptions options) {

        Objects.requireNonNull(materialized, "materialized");
        Objects.requireNonNull(importResult, "importResult");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(options, "options");

        Map<String, OsmNodeRecord> nodes = importResult.nodes();

        Set<String> acceptedWayIds = new TreeSet<>();
        for (OsmWayRecord w : importResult.ways().values()) {
            if (config.resolveRule(w.tags()) != null && w.nodeRefs().size() >= 2) {
                acceptedWayIds.add(w.id());
            }
        }

        Set<String> transitStopNodes = transitStopOsmNodeIds(materialized);
        Set<String> restrictionViaNodes = restrictionViaNodeIds(importResult);

        Map<String, OsmNodeClassification> classification = OsmNodeClassifier.classify(
                importResult, acceptedWayIds, transitStopNodes, restrictionViaNodes,
                options.preserveSharpBends(), options.sharpBendAngleDegrees(),
                options.explicitPreserveOsmNodeIds());

        List<OsmImportIssue> issues = new ArrayList<>(materialized.issues());

        Network network = new Network("osm-simplified-network");
        for (String osmNodeId : new TreeSet<>(classification.keySet())) {
            if (!classification.get(osmNodeId).keep()) {
                continue;
            }
            OsmNodeRecord rec = nodes.get(osmNodeId);
            if (rec == null) {
                continue;
            }
            network.createNode(OsmGeneratedIds.nodeId(osmNodeId),
                    rec.projectedCoord().getX(), rec.projectedCoord().getY());
        }

        Map<String, OsmCollapsedLink> collapsedLinks = new TreeMap<>();
        Map<String, List<String>> linkIdsByOsmWayId = new TreeMap<>();
        Map<String, OsmPolyline> geometry = new TreeMap<>();

        OsmModeAccessResolver access = new OsmModeAccessResolver();
        boolean rawTagsKept = importResult.provenance().rawTagsKept();

        for (String wayId : new TreeSet<>(acceptedWayIds)) {
            OsmWayRecord way = importResult.ways().get(wayId);
            OsmWayRule rule = config.resolveRule(way.tags());
            for (OsmModeAccessResolver.DirectionDecision decision
                    : access.resolve(way, rule.allowedModes())) {
                if (decision.allowedModes().isEmpty()) {
                    continue;
                }
                boolean oneway = !(decision.forward() && decision.backward());
                if (decision.forward()) {
                    processChain(network, way, rule, nodes, classification,
                            new ArrayList<>(way.nodeRefs()), true, decision.allowedModes(), oneway,
                            rawTagsKept, collapsedLinks, linkIdsByOsmWayId, geometry, issues);
                }
                if (decision.backward()) {
                    processChain(network, way, rule, nodes, classification,
                            reverse(way.nodeRefs()), false, decision.allowedModes(), oneway,
                            rawTagsKept, collapsedLinks, linkIdsByOsmWayId, geometry, issues);
                }
            }
        }

        network.postProcess();

        OsmNetworkBuildResult view = new OsmNetworkBuildResult(
                network, List.of(), Collections.emptyMap(), linkIdsByOsmWayId);
        OsmTurnRestrictionReader.Record restrictionRecord =
                OsmTurnRestrictionReader.read(importResult, view);
        issues.addAll(restrictionRecord.issues());

        List<JunctionSignalDescriptor> junctions = buildJunctions(
                network, nodes, importResult.ways(), classification, restrictionRecord.index(), options);
        Map<String, JunctionSignalDescriptor> junctionsByNodeId = new TreeMap<>();
        for (JunctionSignalDescriptor j : junctions) {
            // Index every cluster member, not just the primary, so any member node
            // resolves back to its (possibly multi-node) junction.
            for (String osmId : j.osmNodeIds()) {
                junctionsByNodeId.put(OsmGeneratedIds.nodeId(osmId), j);
            }
        }

        int collapsed = 0;
        int preserved = 0;
        int signalNodes = 0;
        for (String osmNodeId : classification.keySet()) {
            if (classification.get(osmNodeId).keep()) {
                preserved++;
                OsmNodeRecord rec = nodes.get(osmNodeId);
                if (rec != null && OsmNodeClassifier.isSignalized(rec.tags())) {
                    signalNodes++;
                }
            } else {
                collapsed++;
            }
        }

        Map<String, OsmWayRecord> ways = importResult.ways();
        List<OsmImportIssue> reportIssues =
                diagnostics(junctions, network, ways, junctionsByNodeId);
        List<OsmImportIssue> allIssues = new ArrayList<>(issues);
        allIssues.addAll(reportIssues);

        SignalReadinessReport report = new SignalReadinessReport(
                junctions.size(),
                (int) junctions.stream().filter(JunctionSignalDescriptor::confirmedSignalized).count(),
                signalNodes, collapsed, preserved,
                countHighDegreeNonSignalized(network, junctionsByNodeId),
                countMovementsWithoutLaneInfo(junctions, network, ways),
                reportIssues);

        return new OsmSimplifiedNetwork(
                network, new OsmGeometryStore(geometry), collapsedLinks, linkIdsByOsmWayId,
                junctions, junctionsByNodeId,
                restrictionRecord.index(), restrictionRecord.perLink(),
                report, allIssues);
    }

    /** Splits an ordered chain at kept nodes and emits one merged link per kept-to-kept span. */
    private static void processChain(
            Network network, OsmWayRecord way, OsmWayRule rule,
            Map<String, OsmNodeRecord> nodes, Map<String, OsmNodeClassification> classification,
            List<String> ordered, boolean forward, Set<String> modes, boolean oneway,
            boolean rawTagsKept,
            Map<String, OsmCollapsedLink> collapsedLinks,
            Map<String, List<String>> linkIdsByOsmWayId,
            Map<String, OsmPolyline> geometry,
            List<OsmImportIssue> issues) {

        int n = ordered.size();
        if (n < 2) {
            return;
        }

        int m = way.nodeRefs().size();

        List<Integer> kept = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            OsmNodeClassification c = classification.get(ordered.get(i));
            if (c != null && c.keep() && nodes.get(ordered.get(i)) != null) {
                kept.add(i);
            }
        }
        if (kept.isEmpty()) {
            return;
        }

        double dirLanes = new OsmLaneResolver().resolve(way, rule, forward, oneway);
        double dirSpeed = new OsmSpeedResolver().resolve(way, rule, forward);
        double capacityPerLane = rule.capacityPerLane();

        for (int k = 0; k + 1 < kept.size(); k++) {
            int p = kept.get(k);
            int q = kept.get(k + 1);
            if (q <= p) {
                continue;
            }
            String fromOsm = ordered.get(p);
            String toOsm = ordered.get(q);

            List<Coord> pts = new ArrayList<>();
            boolean allRecorded = true;
            for (int i = p; i <= q; i++) {
                OsmNodeRecord rec = nodes.get(ordered.get(i));
                if (rec == null) {
                    allRecorded = false;
                    break;
                }
                pts.add(rec.projectedCoord());
            }
            if (!allRecorded) {
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "simplify-missing-node",
                        "Way " + way.id() + " span " + fromOsm + "->" + toOsm
                                + " references a missing node; span skipped", null));
                continue;
            }

            double length = 0;
            for (int i = 0; i + 1 < pts.size(); i++) {
                double dx = pts.get(i).getX() - pts.get(i + 1).getX();
                double dy = pts.get(i).getY() - pts.get(i + 1).getY();
                length += Math.sqrt(dx * dx + dy * dy);
            }
            if (length <= 0.0) {
                continue;
            }

            List<OsmLinkRef> sourceSegments = new ArrayList<>();
            for (int i = p; i < q; i++) {
                // Position-based forward index (robust to repeated/closed-node ways).
                int ia = forward ? i : (m - 1 - i);
                int ib = forward ? (i + 1) : (m - 2 - i);
                int seg = Math.min(ia, ib);
                boolean segFwd = ib > ia;
                sourceSegments.add(new OsmLinkRef(
                        OsmGeneratedIds.linkId(way.id(), seg, segFwd), way.id(), seg, segFwd));
            }

            String linkId = OsmGeneratedIds.simplifiedLinkId(way.id(), forward, fromOsm, toOsm);
            Link link = network.createLink(linkId,
                    OsmGeneratedIds.nodeId(fromOsm), OsmGeneratedIds.nodeId(toOsm),
                    length, dirLanes * capacityPerLane, dirSpeed, dirLanes, modes);
            link.getAttributes().putAttribute("osm:wayId", way.id());
            link.getAttributes().putAttribute("osm:key", rule.key());
            link.getAttributes().putAttribute("osm:value", rule.value());
            link.getAttributes().putAttribute("osm:simplified", "true");
            link.getAttributes().putAttribute("osm:segmentCount", String.valueOf(q - p));
            copyLaneTags(link, way);
            if (rawTagsKept) {
                for (Map.Entry<String, String> e : way.tags().asMap().entrySet()) {
                    link.getAttributes().putAttribute("osm:tag:" + e.getKey(), e.getValue());
                }
            }

            collapsedLinks.put(linkId,
                    new OsmCollapsedLink(linkId, forward, fromOsm, toOsm, sourceSegments));
            geometry.put(linkId, new OsmPolyline(pts));
            linkIdsByOsmWayId.computeIfAbsent(way.id(), key -> new ArrayList<>()).add(linkId);
        }
    }

    /**
     * Always copy lane-relevant way tags onto the merged link so lane data survives simplification.
     *
     * <p>Review: these values are carried through as <em>raw whole-way OSM source data</em>. A single
     * merged link can span several physical spans and directions, so the original way tags do NOT
     * describe any particular approach or span of the merged link. We therefore annotate the link
     * with an explicit scope/applicability marker so downstream signal-IO consumers treat them as
     * raw provenance, not as resolved per-approach / per-span lane assignments.
     */
    private static void copyLaneTags(Link link, OsmWayRecord way) {
        String[] laneKeys = {"lanes", "lanes:forward", "lanes:backward",
                "turn:lanes", "turn:lanes:forward", "turn:lanes:backward",
                "bus:lanes", "psv:lanes", "taxi:lanes", "bicycle:lanes"};
        boolean any = false;
        for (String key : laneKeys) {
            String value = way.tags().get(key);
            if (value != null) {
                link.getAttributes().putAttribute("osm:tag:" + key, value);
                any = true;
            }
        }
        if (any) {
            link.getAttributes().putAttribute("osm:laneTags.scope", "raw-source");
            link.getAttributes().putAttribute("osm:laneTags.applicability", "whole-way");
        }
    }

    private static List<String> reverse(List<String> in) {
        List<String> out = new ArrayList<>(in.size());
        for (int i = in.size() - 1; i >= 0; i--) {
            out.add(in.get(i));
        }
        return out;
    }

    private static Set<String> transitStopOsmNodeIds(OsmNetworkBuildResult materialized) {
        Set<String> ids = new TreeSet<>();
        for (OsmStopHint h : materialized.stopHints()) {
            if (h.elementType() == OsmElementType.NODE) {
                ids.add(h.osmId());
            }
        }
        return ids;
    }

    private static Set<String> restrictionViaNodeIds(OsmImportResult importResult) {
        Set<String> ids = new TreeSet<>();
        for (OsmRelationRecord rel : importResult.relations().values()) {
            String type = rel.tags().get("type");
            if (!("restriction".equals(type) || "keep_right".equals(type) || "keep_left".equals(type))) {
                continue;
            }
            for (OsmRelationMemberRecord m : rel.members()) {
                if ("via".equals(m.role()) && m.type() == OsmElementType.NODE) {
                    ids.add(m.ref());
                }
            }
        }
        return ids;
    }

    /** Kept network nodes with a high incident degree that are not part of a signalized junction. */
    private static int countHighDegreeNonSignalized(
            Network network, Map<String, JunctionSignalDescriptor> junctionsByNodeId) {
        int count = 0;
        for (Map.Entry<Id<Node>, Node> e : network.getNodes().entrySet()) {
            Node node = e.getValue();
            int degree = node.getInLinks().size() + node.getOutLinks().size();
            if (degree >= 4 && junctionsByNodeId.get(e.getKey().toString()) == null) {
                count++;
            }
        }
        return count;
    }

    /** Movements at junctions whose approach carries turn-lane source data we do not decompose here. */
    private static int countMovementsWithoutLaneInfo(
            List<JunctionSignalDescriptor> junctions, Network network, Map<String, OsmWayRecord> ways) {
        int count = 0;
        for (JunctionSignalDescriptor j : junctions) {
            if (approachHasTurnLanes(j, network, ways)) {
                count += j.movements().size();
            }
        }
        return count;
    }

    /** Enumerate signalized junctions and their controlled movements from the collapsed network. */
    private static List<JunctionSignalDescriptor> buildJunctions(
            Network network, Map<String, OsmNodeRecord> nodes, Map<String, OsmWayRecord> ways,
            Map<String, OsmNodeClassification> classification,
            TurnRestrictionIndex index, OsmSimplifyOptions options) {

        List<String> signalized = new ArrayList<>();
        for (String osmId : new TreeSet<>(classification.keySet())) {
            OsmNodeClassification c = classification.get(osmId);
            if (c.keep() && c.reasons().contains(OsmNodeReason.SIGNALIZED) && nodes.containsKey(osmId)) {
                signalized.add(osmId);
            }
        }

        List<Cluster> clusters = cluster(
                signalized, network, ways,
                options.junctionClusterDistanceMeters(), options.maxClusterHops());

        List<JunctionSignalDescriptor> result = new ArrayList<>();
        for (Cluster cluster : clusters) {
            result.add(buildOneJunction(network, nodes, ways, cluster.members(), index,
                    cluster.witnesses()));
        }
        return result;
    }

    /**
     * A junction cluster plus the per-pair internal witness paths that justified its membership.
     * Each witness is a node-id path (forward direction preserved) that movement reachability is
     * restricted to, so movement reachability uses exactly the internal paths that clustering
     * accepted rather than an unconstrained global BFS (Review #2).
     */
    record Cluster(List<String> members, List<List<String>> witnesses) {
        Cluster {
            members = List.copyOf(members);
            witnesses = List.copyOf(witnesses);
        }
    }

    /**
     * Topology-gated clustering of signalized nodes.
     *
     * <p>With a non-positive threshold (the default) no clustering happens and every signalized
     * node is its own junction. With a positive threshold, two signalized nodes cluster only if a
     * plausible path of <em>junction-internal</em> roads connects them, capped by
     * {@code maxHops} and total length, and never transiting another signalized node.
     *
     * <p>Review: raw Euclidean proximity plus transitive union-find merged distinct intersections.
     * Here the connectivity basis is topological and OSM-idiomatic: only junction-internal link
     * roads (highway classes tagged {@code _link}, e.g. {@code primary_link}, plus bare
     * {@code highway=link}) connect two signalized nodes into one junction. A normal street between
     * two separate intersections is therefore never a clustering edge, and a signalized node is
     * never traversed, so A-B-C chaining across separate intersections is impossible.
     */
    private static List<Cluster> cluster(List<String> osmIds, Network network,
                                         Map<String, OsmWayRecord> ways,
                                         double threshold, int maxHops) {
        int n = osmIds.size();
        List<Cluster> out = new ArrayList<>();
        if (threshold <= 0.0) {
            for (String osmId : osmIds) {
                List<String> singleton = new ArrayList<>();
                singleton.add(osmId);
                out.add(new Cluster(singleton, List.of()));
            }
            return out;
        }

        Map<String, List<String>> adj = internalAdjacency(network, ways);
        Map<String, Double> len = internalEdgeLengths(network, ways);
        Set<String> signalNetIds = new HashSet<>();
        for (String osmId : osmIds) {
            signalNetIds.add(OsmGeneratedIds.nodeId(osmId));
        }

        // Candidate merge relations: an internal path (no other signal in between) of length <=
        // threshold and <= maxHops, in each direction independently (membership is direction
        // independent; movement direction is preserved by keeping both directed witness paths).
        List<Edge> edges = new ArrayList<>();
        Set<Long> qualifying = new HashSet<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String a = OsmGeneratedIds.nodeId(osmIds.get(i));
                String b = OsmGeneratedIds.nodeId(osmIds.get(j));
                List<String> ab = shortestInternalPath(a, b, signalNetIds, adj, len, threshold, maxHops);
                List<String> ba = shortestInternalPath(b, a, signalNetIds, adj, len, threshold, maxHops);
                if (ab == null && ba == null) {
                    continue;
                }
                double best = Double.MAX_VALUE;
                if (ab != null) {
                    best = Math.min(best, pathLength(ab, len));
                }
                if (ba != null) {
                    best = Math.min(best, pathLength(ba, len));
                }
                edges.add(new Edge(i, j, best, ab, ba));
                qualifying.add(pair(i, j));
            }
        }
        edges.sort(Comparator.comparingDouble(Edge::length)
                .thenComparingInt(Edge::i).thenComparingInt(Edge::j));

        // Agglomerate shortest pairs first, but a merge is accepted only when EVERY cross pair
        // between the two components qualifies. This makes each cluster a clique of mutually
        // qualifying members, so A-B and B-C qualifying can never glue A-C into one junction when
        // A-C does not itself qualify (Review #1: no transitive chaining).
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (Edge e : edges) {
            int ri = find(parent, e.i());
            int rj = find(parent, e.j());
            if (ri == rj) {
                continue;
            }
            List<Integer> a = component(parent, ri, n);
            List<Integer> b = component(parent, rj, n);
            if (allCrossPairsQualify(a, b, qualifying) && internallyConnected(a, b, edges)) {
                union(parent, ri, rj);
            }
        }

        Map<Integer, List<String>> byRoot = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(osmIds.get(i));
        }
        for (List<String> grp : byRoot.values()) {
            grp.sort(String::compareTo);
            if (grp.size() == 1) {
                out.add(new Cluster(grp, List.of()));
                continue;
            }
            Set<Integer> memberIdx = new HashSet<>();
            for (String m : grp) {
                memberIdx.add(osmIds.indexOf(m));
            }
            // Review #1: retain ALL accepted directed witnesses between final members, not just a
            // spanning-tree subset, so a qualifying direct A->C witness is never lost merely because
            // A-B/B-C were chosen for the tree and their directions do not compose A->C.
            List<List<String>> witnesses = new ArrayList<>();
            for (Edge e : edges) {
                if (memberIdx.contains(e.i()) && memberIdx.contains(e.j())) {
                    if (e.pathForward() != null) {
                        witnesses.add(e.pathForward());
                    }
                    if (e.pathBackward() != null) {
                        witnesses.add(e.pathBackward());
                    }
                }
            }
            out.add(new Cluster(grp, witnesses));
        }
        out.sort(Comparator.comparing(c -> c.members().get(0)));
        return out;
    }

    private record Edge(int i, int j, double length, List<String> pathForward, List<String> pathBackward) { }

    private static long pair(int i, int j) {
        int lo = Math.min(i, j);
        int hi = Math.max(i, j);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }

    private static List<Integer> component(int[] parent, int root, int n) {
        List<Integer> members = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (find(parent, i) == root) {
                members.add(i);
            }
        }
        return members;
    }

    private static boolean allCrossPairsQualify(List<Integer> a, List<Integer> b, Set<Long> qualifying) {
        for (int x : a) {
            for (int y : b) {
                if (!qualifying.contains(pair(x, y))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Sanity check that the two components are joined by at least one qualifying edge. */
    private static boolean internallyConnected(List<Integer> a, List<Integer> b, List<Edge> edges) {
        Set<Integer> set = new HashSet<>(a);
        set.addAll(b);
        for (Edge e : edges) {
            if (set.contains(e.i()) && set.contains(e.j())) {
                return true;
            }
        }
        return false;
    }

    private static double pathLength(List<String> path, Map<String, Double> len) {
        double total = 0.0;
        for (int i = 0; i + 1 < path.size(); i++) {
            total += len.getOrDefault(path.get(i) + "|" + path.get(i + 1), 0.0);
        }
        return total;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) {
            p[i] = p[p[i]];
            i = p[i];
        }
        return i;
    }

    private static void union(int[] p, int a, int b) {
        p[find(p, a)] = find(p, b);
    }

    /**
     * True when the link's source OSM way is a link road (ramp/connector/internal box road). Review
     * #1: OSM tags these as the parent highway class with a {@code _link} suffix (e.g.
     * {@code primary_link}), plus a bare {@code highway=link} for unclassified minor links. These are
     * the only roads treated as "inside the same junction".
     */
    private static boolean isInternalJunctionLink(Link link, Map<String, OsmWayRecord> ways) {
        Object wayId = link.getAttributes().getAttribute("osm:wayId");
        if (wayId == null) {
            return false;
        }
        OsmWayRecord way = ways.get(String.valueOf(wayId));
        return way != null && isLinkHighway(way.tags().get("highway"));
    }

    /**
     * An OSM highway value denotes a link road iff it is {@code link} or carries the standard
     * {@code _link} class suffix (motorway_link, trunk_link, primary_link, secondary_link,
     * tertiary_link, ...). Exposed for tests.
     */
    static boolean isLinkHighway(String highway) {
        return "link".equals(highway) || (highway != null && highway.endsWith("_link"));
    }

    /**
     * Shortest directed path (by internal-link length, then hop count) from {@code a} to {@code b}
     * over junction-internal roads, capped by {@code threshold} total length and {@code maxHops},
     * never transiting another signalized node. Returns the node-id path (including endpoints), or
     * {@code null} if none exists. The path is the "junction box" witness used for both clustering
     * acceptance and movement reachability.
     */
    static List<String> shortestInternalPath(String a, String b, Set<String> signalNetIds,
                                                     Map<String, List<String>> adj,
                                                     Map<String, Double> len,
                                                     double threshold, int maxHops) {
        if (a.equals(b)) {
            return List.of(a);
        }
        // Resource-constrained shortest path: feasibility is bounded by BOTH distance and hop count,
        // so a single shortest-distance label per node is not sufficient (a shorter path that burns
        // hops can block a slightly longer but fewer-hop path that can still finish). State is
        // (node, hops), each holding its best distance; the predecessor chain is kept per state.
        Map<String, Double> best = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<SearchState> pq = new PriorityQueue<>(
                Comparator.comparingDouble(SearchState::dist)
                        .thenComparingInt(SearchState::hops)
                        .thenComparing(SearchState::node));

        String startKey = key(a, 0);
        best.put(startKey, 0.0);
        pq.add(new SearchState(0.0, 0, a));

        while (!pq.isEmpty()) {
            SearchState cur = pq.poll();
            String curKey = key(cur.node(), cur.hops());
            if (cur.dist() > best.getOrDefault(curKey, Double.MAX_VALUE)) {
                continue;
            }
            if (cur.node().equals(b)) {
                return reconstructPath(prev, curKey);
            }
            if (cur.hops() >= maxHops) {
                continue;
            }
            for (String v : adj.getOrDefault(cur.node(), List.of())) {
                if (v.equals(a)) {
                    continue;
                }
                // May only pass THROUGH non-signalized nodes; {@code b} is the sole allowed sink.
                if (signalNetIds.contains(v) && !v.equals(b)) {
                    continue;
                }
                double nd = cur.dist() + len.getOrDefault(cur.node() + "|" + v, 0.0);
                int nh = cur.hops() + 1;
                if (nd > threshold || nh > maxHops) {
                    continue;
                }
                String nextKey = key(v, nh);
                Double pv = best.get(nextKey);
                if (pv == null || nd < pv) {
                    best.put(nextKey, nd);
                    prev.put(nextKey, curKey);
                    pq.add(new SearchState(nd, nh, v));
                }
            }
        }
        return null;
    }

    private record SearchState(double dist, int hops, String node) { }

    private static String key(String node, int hops) {
        return node + "@" + hops;
    }

    private static List<String> reconstructPath(Map<String, String> prev, String endKey) {
        LinkedList<String> path = new LinkedList<>();
        for (String cur = endKey; cur != null; cur = prev.get(cur)) {
            path.addFirst(cur.substring(0, cur.lastIndexOf('@')));
        }
        return path;
    }

    /** Directed length of every junction-internal link, keyed {@code from|to} (min over parallels). */
    private static Map<String, Double> internalEdgeLengths(Network network, Map<String, OsmWayRecord> ways) {
        Map<String, Double> len = new HashMap<>();
        for (Link link : network.getLinks().values()) {
            if (!isInternalJunctionLink(link, ways)) {
                continue;
            }
            String a = link.getFromNode().getId().toString();
            String b = link.getToNode().getId().toString();
            if (a.equals(b)) {
                continue;
            }
            String key = a + "|" + b;
            Double cur = len.get(key);
            if (cur == null || link.getLength() < cur) {
                len.put(key, link.getLength());
            }
        }
        return len;
    }

    private static JunctionSignalDescriptor buildOneJunction(
            Network network, Map<String, OsmNodeRecord> nodes, Map<String, OsmWayRecord> ways,
            List<String> cluster, TurnRestrictionIndex index, List<List<String>> witnesses) {

        Set<String> clusterNetNodeIds = new TreeSet<>();
        for (String osmId : cluster) {
            clusterNetNodeIds.add(OsmGeneratedIds.nodeId(osmId));
        }
        Map<String, Integer> memberIdx = new HashMap<>();
        for (int i = 0; i < cluster.size(); i++) {
            memberIdx.put(OsmGeneratedIds.nodeId(cluster.get(i)), i);
        }
        boolean[][] reach = witnessReachability(cluster, witnesses);

        // Review #4: internal junction-box links (the `*_link` connectors that justify clustering)
        // must not be advertised as signal-facing approaches/departures. A link is internal only when
        // BOTH endpoints lie inside the junction box, i.e. are cluster members or non-signal nodes on
        // an accepted internal witness path. A `*_link` leaving a junction to an external node is a
        // real boundary approach/departure, not an internal connector.
        Set<String> internalNodes = new TreeSet<>(clusterNetNodeIds);
        for (List<String> path : witnesses) {
            internalNodes.addAll(path);
        }
        Set<String> internalNetLinks = new TreeSet<>();
        for (Link link : network.getLinks().values()) {
            String from = link.getFromNode().getId().toString();
            String to = link.getToNode().getId().toString();
            if (internalNodes.contains(from) && internalNodes.contains(to)) {
                internalNetLinks.add(link.getId().toString());
            }
        }

        List<Link> incoming = new ArrayList<>();
        List<Link> outgoing = new ArrayList<>();
        for (Link link : network.getLinks().values()) {
            if (internalNetLinks.contains(link.getId().toString())) {
                continue;
            }
            if (clusterNetNodeIds.contains(link.getToNode().getId().toString())) {
                incoming.add(link);
            }
            if (clusterNetNodeIds.contains(link.getFromNode().getId().toString())) {
                outgoing.add(link);
            }
        }
        incoming.sort(Comparator.comparing(l -> l.getId().toString()));
        outgoing.sort(Comparator.comparing(l -> l.getId().toString()));

        List<String> inIds = incoming.stream().map(l -> l.getId().toString()).toList();
        List<String> outIds = outgoing.stream().map(l -> l.getId().toString()).toList();

        List<SignalizedMovement> movements = new ArrayList<>();
        for (Link in : incoming) {
            Integer inM = memberIdx.get(in.getToNode().getId().toString());
            for (Link out : outgoing) {
                if (in.getId().equals(out.getId())) {
                    continue;
                }
                // A movement is only physically valid when the departure member is reachable from
                // the arrival member through the witness paths that justified clustering.
                Integer outM = memberIdx.get(out.getFromNode().getId().toString());
                if (inM == null || outM == null || !reach[inM][outM]) {
                    continue;
                }
                Set<String> controlled = new TreeSet<>(in.getAllowedModes());
                controlled.retainAll(out.getAllowedModes());
                if (controlled.isEmpty()) {
                    continue;
                }
                Set<String> restricted = new TreeSet<>();
                if (index != null) {
                    for (String mode : controlled) {
                        if (index.isDisallowed(mode, in.getId().toString(), out.getId().toString())) {
                            restricted.add(mode);
                        }
                    }
                }
                movements.add(new SignalizedMovement(in.getId().toString(), out.getId().toString(),
                        turnType(in, out), controlled, restricted));
            }
        }

        String primary = cluster.get(0);
        return new JunctionSignalDescriptor(
                "signal_" + primary, primary, cluster, true, confidenceFor(nodes.get(primary)),
                provenanceFor(primary, cluster, nodes.get(primary)), inIds, outIds,
                new ArrayList<>(internalNetLinks), movements);
    }

    private static int confidenceFor(OsmNodeRecord rec) {
        if (rec == null) {
            return 1;
        }
        String ts = rec.tags().get("traffic_signals");
        if (ts != null && !"no".equals(ts) && !"0".equals(ts) && !"none".equals(ts)) {
            return 3;
        }
        if (rec.tags().has("highway", "traffic_signals")) {
            return 2;
        }
        return 1;
    }

    private static String provenanceFor(String primary, List<String> cluster, OsmNodeRecord rec) {
        StringBuilder sb = new StringBuilder("osm_node=").append(primary)
                .append(";clusterSize=").append(cluster.size()).append(";tag=");
        if (rec == null) {
            sb.append("none");
        } else if (rec.tags().get("traffic_signals") != null) {
            sb.append("traffic_signals=").append(rec.tags().get("traffic_signals"));
        } else if (rec.tags().has("highway", "traffic_signals")) {
            sb.append("highway=traffic_signals");
        } else {
            sb.append("inferred");
        }
        return sb.toString();
    }

    /**
     * Directed reachability between cluster members restricted to the internal witness paths that
     * justified the cluster (Review #2). Each witness is a directed node-id path; a directed edge
     * is admitted only when it is a consecutive step of some accepted witness, so reachability can
     * never leave the junction box or transit another signal through a path that clustering did not
     * accept. Directional, so a one-way witness yields only its forward movement.
     */
    static boolean[][] witnessReachability(List<String> cluster, List<List<String>> witnesses) {
        int n = cluster.size();
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) {
            idx.put(OsmGeneratedIds.nodeId(cluster.get(i)), i);
        }
        Map<String, Set<String>> witnessAdj = new HashMap<>();
        for (List<String> path : witnesses) {
            for (int i = 0; i + 1 < path.size(); i++) {
                witnessAdj.computeIfAbsent(path.get(i), k -> new TreeSet<>()).add(path.get(i + 1));
            }
        }
        boolean[][] reach = new boolean[n][n];
        for (int s = 0; s < n; s++) {
            String start = OsmGeneratedIds.nodeId(cluster.get(s));
            Deque<String> dq = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            dq.add(start);
            seen.add(start);
            reach[s][s] = true;
            while (!dq.isEmpty()) {
                String u = dq.poll();
                for (String v : witnessAdj.getOrDefault(u, Set.of())) {
                    if (!seen.add(v)) {
                        continue;
                    }
                    Integer vi = idx.get(v);
                    if (vi != null) {
                        reach[s][vi] = true;
                    }
                    dq.add(v);
                }
            }
        }
        return reach;
    }

    /**
     * Directed adjacency over the junction-internal link-road subgraph (all endpoints, not just
     * signal members). This is the single connectivity basis shared by clustering (#3) and
     * movement reachability (#2), so both operations see the same internal junction.
     */
    static Map<String, List<String>> internalAdjacency(Network network, Map<String, OsmWayRecord> ways) {
        Map<String, List<String>> adj = new TreeMap<>();
        for (Link link : network.getLinks().values()) {
            if (!isInternalJunctionLink(link, ways)) {
                continue;
            }
            String a = link.getFromNode().getId().toString();
            String b = link.getToNode().getId().toString();
            if (a.equals(b)) {
                continue;
            }
            adj.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
        }
        for (List<String> v : adj.values()) {
            v.sort(String::compareTo);
        }
        return adj;
    }

    /**
     * Best-effort turn classification from arrival and departure geometry. Review: only the two
     * endpoint nodes are meaningful when the turn actually happens at one node; for a
     * multi-node clustered movement the arrival and departure occur at different nodes, so we
     * report {@code UNKNOWN} rather than a bogus angle between unrelated vectors.
     */
    private static OsmTurnType turnType(Link in, Link out) {
        if (!in.getToNode().getId().equals(out.getFromNode().getId())) {
            return OsmTurnType.UNKNOWN;
        }
        Coord prev = in.getFromNode().getCoord();
        Coord cur = in.getToNode().getCoord();
        Coord dep = out.getFromNode().getCoord();
        Coord next = out.getToNode().getCoord();
        double ix = cur.getX() - prev.getX();
        double iy = cur.getY() - prev.getY();
        double ox = next.getX() - dep.getX();
        double oy = next.getY() - dep.getY();
        double im = Math.hypot(ix, iy);
        double om = Math.hypot(ox, oy);
        if (im < 1e-6 || om < 1e-6) {
            return OsmTurnType.UNKNOWN;
        }
        double cos = (ix * ox + iy * oy) / (im * om);
        double cross = ix * oy - iy * ox;
        if (cos < -0.98) {
            return OsmTurnType.U_TURN;
        }
        if (Math.abs(cross) < 0.2 * im * om) {
            return OsmTurnType.THROUGH;
        }
        return cross > 0 ? OsmTurnType.LEFT : OsmTurnType.RIGHT;
    }

    /** Signal-readiness diagnostics: fatal/readiness findings on junctions and high-degree nodes. */
    private static List<OsmImportIssue> diagnostics(
            List<JunctionSignalDescriptor> junctions, Network network,
            Map<String, OsmWayRecord> ways, Map<String, JunctionSignalDescriptor> junctionsByNodeId) {

        List<OsmImportIssue> issues = new ArrayList<>();
        for (JunctionSignalDescriptor j : junctions) {
            if (j.incomingLinks().isEmpty()) {
                issues.add(issue(OsmIssueSeverity.WARNING, "signalized-no-incoming",
                        "Junction " + j.junctionId() + " has no incoming approach links"));
            }
            if (j.outgoingLinks().isEmpty()) {
                issues.add(issue(OsmIssueSeverity.WARNING, "signalized-no-outgoing",
                        "Junction " + j.junctionId() + " has no outgoing links"));
            }
            for (String in : j.incomingLinks()) {
                // An approach counts as legal if ANY outgoing movement is legal for at least one
                // controlled mode (a bus-legal, car-restricted movement is a usable approach).
                boolean anyLegal =
                        j.movementsForIncoming(in).stream().anyMatch(SignalizedMovement::anyLegal);
                if (!anyLegal) {
                    issues.add(issue(OsmIssueSeverity.WARNING, "approach-no-legal-outgoing",
                            "Junction " + j.junctionId() + " approach " + in
                                    + " has no legal outgoing movement"));
                }
            }
            if (approachHasTurnLanes(j, network, ways)) {
                issues.add(issue(OsmIssueSeverity.INFO, "ambiguous-lane-tags",
                        "Junction " + j.junctionId()
                                + " approaches carry turn:lanes data not decomposed into per-movement lanes"));
            }
        }
        for (Map.Entry<Id<Node>, Node> e : network.getNodes().entrySet()) {
            Node node = e.getValue();
            int degree = node.getInLinks().size() + node.getOutLinks().size();
            if (degree >= 4 && junctionsByNodeId.get(e.getKey().toString()) == null) {
                issues.add(issue(OsmIssueSeverity.INFO, "non-signalized-high-degree",
                        "Node " + e.getKey() + " has high degree but is not a signalized junction"));
            }
        }
        return issues;
    }

    private static OsmImportIssue issue(OsmIssueSeverity severity, String code, String message) {
        return new OsmImportIssue(severity, code, message, null);
    }

    /** True when any approach of the junction belongs to a way with turn:lanes source data. */
    private static boolean approachHasTurnLanes(
            JunctionSignalDescriptor j, Network network, Map<String, OsmWayRecord> ways) {
        for (String linkId : j.incomingLinks()) {
            Link link = network.getLinks().get(Id.create(linkId, Link.class));
            if (link == null) {
                continue;
            }
            Object wayIdAttr = link.getAttributes().getAttribute("osm:wayId");
            if (wayIdAttr == null) {
                continue;
            }
            OsmWayRecord way = ways.get(String.valueOf(wayIdAttr));
            if (way != null && wayHasTurnLanes(way)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wayHasTurnLanes(OsmWayRecord way) {
        for (String key : way.tags().asMap().keySet()) {
            if (key.startsWith("turn:lanes")) {
                return true;
            }
        }
        return false;
    }
}
