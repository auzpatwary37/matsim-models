package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                network, nodes, classification, restrictionRecord.index(), options);
        Map<String, JunctionSignalDescriptor> junctionsByNodeId = new TreeMap<>();
        for (JunctionSignalDescriptor j : junctions) {
            junctionsByNodeId.put(OsmGeneratedIds.nodeId(j.primaryOsmNodeId()), j);
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

        List<OsmImportIssue> reportIssues = diagnostics(junctions, network);
        List<OsmImportIssue> allIssues = new ArrayList<>(issues);
        allIssues.addAll(reportIssues);

        SignalReadinessReport report = new SignalReadinessReport(
                junctions.size(),
                (int) junctions.stream().filter(JunctionSignalDescriptor::confirmedSignalized).count(),
                signalNodes, collapsed, preserved,
                countHighDegreeNonSignalized(network, junctionsByNodeId),
                countMovementsWithoutLaneInfo(junctions),
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

        List<String> fwd = way.nodeRefs();
        Map<String, Integer> posOf = new HashMap<>();
        for (int i = 0; i < fwd.size(); i++) {
            posOf.put(fwd.get(i), i);
        }

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
                String a = ordered.get(i);
                String b = ordered.get(i + 1);
                int pa = posOf.get(a);
                int pb = posOf.get(b);
                int seg = Math.min(pa, pb);
                boolean segFwd = pb > pa;
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
            if (rawTagsKept) {
                for (Map.Entry<String, String> e : way.tags().asMap().entrySet()) {
                    link.getAttributes().putAttribute("osm:tag:" + e.getKey(), e.getValue());
                }
            }

            collapsedLinks.put(linkId,
                    new OsmCollapsedLink(linkId, way.id(), forward, fromOsm, toOsm, sourceSegments));
            geometry.put(linkId, new OsmPolyline(pts));
            linkIdsByOsmWayId.computeIfAbsent(way.id(), key -> new ArrayList<>()).add(linkId);
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

    private static int countMovementsWithoutLaneInfo(List<JunctionSignalDescriptor> junctions) {
        int count = 0;
        for (JunctionSignalDescriptor j : junctions) {
            for (SignalizedMovement m : j.movements()) {
                count += (m == null ? 1 : 0);
            }
        }
        return count;
    }

    /** Enumerate signalized junctions and their controlled movements from the collapsed network. */
    private static List<JunctionSignalDescriptor> buildJunctions(
            Network network, Map<String, OsmNodeRecord> nodes,
            Map<String, OsmNodeClassification> classification,
            TurnRestrictionIndex index, OsmSimplifyOptions options) {

        List<String> signalized = new ArrayList<>();
        for (String osmId : new TreeSet<>(classification.keySet())) {
            OsmNodeClassification c = classification.get(osmId);
            if (c.keep() && c.reasons().contains(OsmNodeReason.SIGNALIZED) && nodes.containsKey(osmId)) {
                signalized.add(osmId);
            }
        }

        List<List<String>> clusters =
                cluster(signalized, nodes, options.junctionClusterDistanceMeters());

        List<JunctionSignalDescriptor> result = new ArrayList<>();
        for (List<String> cluster : clusters) {
            result.add(buildOneJunction(network, nodes, cluster.get(0), cluster, index));
        }
        return result;
    }

    /** Greedy proximity clustering of signalized nodes; each group sorted, groups ordered by min id. */
    private static List<List<String>> cluster(List<String> ids, Map<String, OsmNodeRecord> nodes,
                                              double threshold) {
        int n = ids.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (distance(nodes, ids.get(i), ids.get(j)) <= threshold) {
                    union(parent, i, j);
                }
            }
        }
        Map<Integer, List<String>> byRoot = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(ids.get(i));
        }
        List<List<String>> out = new ArrayList<>();
        for (List<String> grp : byRoot.values()) {
            grp.sort(String::compareTo);
            out.add(grp);
        }
        out.sort(Comparator.comparing(grp -> grp.get(0)));
        return out;
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

    private static double distance(Map<String, OsmNodeRecord> nodes, String a, String b) {
        OsmNodeRecord ra = nodes.get(a);
        OsmNodeRecord rb = nodes.get(b);
        double dx = ra.projectedCoord().getX() - rb.projectedCoord().getX();
        double dy = ra.projectedCoord().getY() - rb.projectedCoord().getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static JunctionSignalDescriptor buildOneJunction(
            Network network, Map<String, OsmNodeRecord> nodes, String primary,
            List<String> cluster, TurnRestrictionIndex index) {

        Set<String> clusterNetNodeIds = new TreeSet<>();
        for (String osmId : cluster) {
            clusterNetNodeIds.add(OsmGeneratedIds.nodeId(osmId));
        }

        List<Link> incoming = new ArrayList<>();
        List<Link> outgoing = new ArrayList<>();
        for (Link link : network.getLinks().values()) {
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
            for (Link out : outgoing) {
                if (in.getId().equals(out.getId())) {
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

        return new JunctionSignalDescriptor(
                "signal_" + primary, primary, cluster, true, confidenceFor(nodes.get(primary)),
                provenanceFor(primary, cluster, nodes.get(primary)), inIds, outIds, movements);
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

    /** Best-effort turn classification from arrival and departure geometry. */
    private static OsmTurnType turnType(Link in, Link out) {
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

    // Populated by the diagnostics step (Task 4).
    private static List<OsmImportIssue> diagnostics(
            List<JunctionSignalDescriptor> junctions, Network network) {
        return List.of();
    }
}
