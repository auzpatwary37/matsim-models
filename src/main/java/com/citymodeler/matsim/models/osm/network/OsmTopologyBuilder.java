package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.osm.OsmElementType;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationMemberRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Cross-way topology contraction engine. Dissolves every maximal chain of non-routing OSM nodes
 * between two routing nodes into a single merged MATSim link, per travel direction, regardless of
 * OSM way boundaries. Equivalent physical roads represented as one way or as several ways split at
 * a degree-2 node therefore collapse to identical topology.
 *
 * <p>Deterministic: all input collections are traversed in sorted order, every output is backed by
 * a {@link TreeMap}/{@link TreeSet}, and each directed source segment is consumed exactly once.
 */
public final class OsmTopologyBuilder {

    private OsmTopologyBuilder() {
    }

    /** Structural + intrinsic-tag contraction used by the plain network build path and unit tests. */
    public static CollapsedTopology build(OsmImportResult importResult,
                                          OsmNetworkBuildConfig config,
                                          boolean keepAllGeometryNodes) {
        return buildCore(importResult, config, null, keepAllGeometryNodes, false, null);
    }

    /**
     * Signal-ready contraction: additionally retains transit-stop nodes and via nodes, and honors
     * the simplification options' sharp-bend / explicit-preserve policy. Always contracts
     * geometry-only nodes (that is its purpose). Transit-stop nodes are derived from node tags.
     */
    public static CollapsedTopology buildSignalReady(OsmImportResult importResult,
                                                     OsmNetworkBuildConfig config,
                                                     OsmSimplifyOptions options) {
        return buildSignalReady(importResult, config, options, null);
    }

    /**
     * Signal-ready contraction with an explicit transit-stop node set. When
     * {@code transitStopNodeIds} is non-null it replaces the tag-derived stop set, letting a caller
     * preserve the stops it materialized (e.g. from importer hints) rather than re-deriving them.
     */
    public static CollapsedTopology buildSignalReady(OsmImportResult importResult,
                                                     OsmNetworkBuildConfig config,
                                                     OsmSimplifyOptions options,
                                                     Set<String> transitStopNodeIds) {
        return buildCore(importResult, config, options, false, true, transitStopNodeIds);
    }

    private static CollapsedTopology buildCore(OsmImportResult importResult,
                                               OsmNetworkBuildConfig config,
                                               OsmSimplifyOptions options,
                                               boolean keepAllGeometryNodes,
                                               boolean signalReady,
                                               Set<String> transitStopNodeIds) {
        List<OsmImportIssue> issues = new ArrayList<>(importResult.issues());

        Set<String> acceptedWayIds = new TreeSet<>();
        for (OsmWayRecord w : importResult.ways().values()) {
            if (config.resolveRule(w.tags()) != null && w.nodeRefs().size() >= 2) {
                acceptedWayIds.add(w.id());
            }
        }

        OsmSegmentGraph graph = OsmSegmentGraph.build(importResult, config);

        Set<String> stopNodes = signalReady
                ? (transitStopNodeIds != null ? transitStopNodeIds : stopNodes(importResult))
                : new TreeSet<>();
        Set<String> viaNodes = viaNodes(importResult);

        boolean preserveSharpBends = options != null && options.preserveSharpBends();
        double sharpBendAngleDegrees = options != null
                ? options.sharpBendAngleDegrees()
                : config.sharpBendAngleDegrees();
        Set<String> explicitPreserve = options != null
                ? options.explicitPreserveOsmNodeIds()
                : config.explicitOsmNodeIdsToKeep();

        Map<String, OsmNodeClassification> classification = OsmNodeClassifier.classifyIntrinsic(
                importResult, acceptedWayIds, stopNodes, viaNodes,
                preserveSharpBends, sharpBendAngleDegrees, explicitPreserve,
                config.preserveCrossingNodes(), config.preserveBarrierNodes());

        Set<String> routing = OsmRoutingNodeSelector.select(graph, classification, keepAllGeometryNodes);
        routing = enforceMaxLinkLength(graph, routing, config.maxContractedLinkLengthMeters());
        routing = anchorCycles(graph, routing);
        Map<String, OsmNodeRecord> nodeRecords = importResult.nodes();
        Map<String, OsmWayRecord> wayRecords = importResult.ways();

        Network network = new Network("osm-contracted-network");
        for (String osmNodeId : new TreeSet<>(routing)) {
            OsmNodeRecord rec = nodeRecords.get(osmNodeId);
            if (rec == null) {
                continue;
            }
            network.createNode(OsmGeneratedIds.nodeId(osmNodeId),
                    rec.projectedCoord().getX(), rec.projectedCoord().getY());
        }

        Map<String, OsmCollapsedLink> collapsedLinks = new TreeMap<>();
        Map<String, List<String>> linkIdsByOsmWayId = new TreeMap<>();
        Map<String, OsmPolyline> geometry = new TreeMap<>();
        boolean rawTagsKept = importResult.provenance().rawTagsKept();

        Set<String> visited = new HashSet<>();

        for (String start : new TreeSet<>(routing)) {
            for (OsmSegmentGraph.Segment firstSeg : graph.segmentsFrom(start)) {
                String neighbour = graph.other(firstSeg, start);
                if (!OsmSegmentGraph.allowsTravel(firstSeg, start, neighbour)) {
                    continue;
                }
                boolean chainForward = firstSeg.nodeA().equals(start)
                        && firstSeg.nodeB().equals(neighbour);
                if (visited.contains(segmentKey(firstSeg, chainForward))) {
                    continue;
                }

                List<OsmLinkRef> sourceSegments = new ArrayList<>();
                List<Coord> pts = new ArrayList<>();
                List<String> chainNodeIds = new ArrayList<>();
                OsmNodeRecord startRec = nodeRecords.get(start);
                if (startRec == null) {
                    continue;
                }
                pts.add(startRec.projectedCoord());
                chainNodeIds.add(start);

                String end = start;
                String cur = start;
                OsmSegmentGraph.Segment seg = firstSeg;
                while (true) {
                    String next = graph.other(seg, cur);
                    if (!OsmSegmentGraph.allowsTravel(seg, cur, next)) {
                        break;
                    }
                    boolean traversedForward = seg.nodeA().equals(cur) && seg.nodeB().equals(next);
                    if (!visited.add(segmentKey(seg, traversedForward))) {
                        break;
                    }
                    sourceSegments.add(new OsmLinkRef(
                            OsmGeneratedIds.linkId(seg.wayId(), seg.segmentIndex(), traversedForward),
                            seg.wayId(), seg.segmentIndex(), traversedForward));
                    OsmNodeRecord nextRec = nodeRecords.get(next);
                    if (nextRec == null) {
                        break;
                    }
                    pts.add(nextRec.projectedCoord());
                    chainNodeIds.add(next);
                    end = next;
                    if (routing.contains(next)) {
                        break;
                    }
                    OsmSegmentGraph.Segment continuation = otherIncident(graph, next, seg);
                    if (continuation == null) {
                        break;
                    }
                    cur = next;
                    seg = continuation;
                }

                if (sourceSegments.isEmpty() || pts.size() < 2) {
                    continue;
                }
                emitLink(network, start, end, chainForward, firstSeg, sourceSegments, pts, chainNodeIds,
                        config, wayRecords, rawTagsKept, keepAllGeometryNodes,
                        collapsedLinks, linkIdsByOsmWayId, geometry, issues);
            }
        }

        network.postProcess();

        // A routing node can survive on a degenerate span (emitLink returns early for zero-length
        // or duplicate canonical spans with no incident link). Reconcile UNCONDITIONALLY so the
        // routing set and classification never advertise a node the network cannot route through.
        reconcileIsolatedRoutingNodes(network, routing, classification, issues);

        List<List<String>> quarantined = List.of();
        if (config.cleanupIsolatedComponents()) {
            OsmNetworkCleaner.CleanResult cleaned =
                    OsmNetworkCleaner.clean(network, config.routableModes(),
                            linkId -> {
                                OsmCollapsedLink collapsed = collapsedLinks.get(linkId);
                                return collapsed == null ? List.of() : collapsed.sourceOsmWayIds();
                            });
            issues.addAll(cleaned.issues());
            quarantined = cleaned.quarantinedComponents();
            reconcileAfterCleanup(network, collapsedLinks, linkIdsByOsmWayId, geometry,
                    classification, routing);
        }

        return new CollapsedTopology(network, collapsedLinks, linkIdsByOsmWayId, geometry,
                classification, new TreeSet<>(routing), issues, quarantined);
    }

    /**
     * Retains additional routing nodes so that no contracted CAR link exceeds {@code cap} metres,
     * mirroring pt2MATSim's {@code maxLinkLength} policy: walk each maximal chain between existing
     * routing nodes and, where the distance since the last kept node would exceed the cap, promote
     * the node at which it exceeds to a routing node. A resulting link may overshoot the cap by at
     * most one atomic segment (as in pt2MATSim). A non-positive cap disables the policy.
     *
     * <p>The cap applies to car roads only: rail/tram tracks are not split by length (pt2MATSim does
     * not length-cap rail, and applying the car cap to rail over-segments it).
     */
    private static Set<String> enforceMaxLinkLength(OsmSegmentGraph graph, Set<String> routingBase,
                                                    double cap) {
        if (cap <= 0.0) {
            return routingBase;
        }
        Set<String> promotions = new TreeSet<>();
        Set<String> visited = new HashSet<>();
        for (String start : new TreeSet<>(routingBase)) {
            for (OsmSegmentGraph.Segment firstSeg : graph.segmentsFrom(start)) {
                String neighbour = graph.other(firstSeg, start);
                if (!OsmSegmentGraph.allowsTravel(firstSeg, start, neighbour)) {
                    continue;
                }
                boolean chainForward = firstSeg.nodeA().equals(start)
                        && firstSeg.nodeB().equals(neighbour);
                if (visited.contains(segmentKey(firstSeg, chainForward))) {
                    continue;
                }
                boolean carRoad = isCarRoad(firstSeg);
                String cur = start;
                OsmSegmentGraph.Segment seg = firstSeg;
                double sinceLastKept = 0.0;
                while (true) {
                    String next = graph.other(seg, cur);
                    if (!OsmSegmentGraph.allowsTravel(seg, cur, next)) {
                        break;
                    }
                    boolean traversedForward = seg.nodeA().equals(cur) && seg.nodeB().equals(next);
                    if (!visited.add(segmentKey(seg, traversedForward))) {
                        break;
                    }
                    if (carRoad) {
                        sinceLastKept += seg.length();
                    }
                    if (routingBase.contains(next)) {
                        break;
                    }
                    if (carRoad && sinceLastKept > cap) {
                        promotions.add(next);
                        sinceLastKept = 0.0;
                    } else if (promotions.contains(next)) {
                        sinceLastKept = 0.0;
                    }
                    OsmSegmentGraph.Segment continuation = otherIncident(graph, next, seg);
                    if (continuation == null) {
                        break;
                    }
                    cur = next;
                    seg = continuation;
                }
            }
        }
        Set<String> result = new TreeSet<>(routingBase);
        result.addAll(promotions);
        return result;
    }

    /** True when the segment permits cars (the road subnetwork the length cap applies to). */
    private static boolean isCarRoad(OsmSegmentGraph.Segment seg) {
        return seg.modes(true).contains("car") || seg.modes(false).contains("car");
    }

    /**
     * Drops routing nodes (and their classification) that no emitted network link references, e.g.
     * a node left stranded when a degenerate zero-length/duplicate span was skipped during emit.
     * Emits one deterministic WARNING per dropped node. Independent of the optional component
     * cleanup: the invariant that a routing node is routable must always hold.
     */
    private static void reconcileIsolatedRoutingNodes(Network network,
                                                      Set<String> routing,
                                                      Map<String, OsmNodeClassification> classification,
                                                      List<OsmImportIssue> issues) {
        Set<String> referencedNodeIds = new TreeSet<>();
        for (Link link : network.getLinks().values()) {
            referencedNodeIds.add(link.getFromNodeId().toString());
            referencedNodeIds.add(link.getToNodeId().toString());
        }
        for (String osmNodeId : new TreeSet<>(routing)) {
            String generated = OsmGeneratedIds.nodeId(osmNodeId);
            if (!referencedNodeIds.contains(generated)) {
                routing.remove(osmNodeId);
                classification.remove(osmNodeId);
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "isolated-routing-node",
                        "Routing node " + generated + " has no incident emitted link "
                                + "(degenerate span); dropping it from the routing set", null));
            }
        }
    }

    /**
     * Drops provenance, geometry, per-way index and classification entries that the optional
     * isolated-component cleanup removed from the network, so {@link CollapsedTopology} never
     * exposes links or nodes that no longer exist. Deterministic: the network maps are
     * {@link java.util.LinkedHashMap}s traversed in insertion order.
     */
    private static void reconcileAfterCleanup(Network network,
                                              Map<String, OsmCollapsedLink> collapsedLinks,
                                              Map<String, List<String>> linkIdsByOsmWayId,
                                              Map<String, OsmPolyline> geometry,
                                              Map<String, OsmNodeClassification> classification,
                                              Set<String> routing) {
        Set<String> survivingLinkIds = new HashSet<>();
        for (Id<Link> linkId : network.getLinks().keySet()) {
            survivingLinkIds.add(linkId.toString());
        }
        collapsedLinks.keySet().retainAll(survivingLinkIds);
        geometry.keySet().retainAll(survivingLinkIds);

        for (String wayId : new ArrayList<>(linkIdsByOsmWayId.keySet())) {
            List<String> ids = linkIdsByOsmWayId.get(wayId);
            ids.retainAll(survivingLinkIds);
            if (ids.isEmpty()) {
                linkIdsByOsmWayId.remove(wayId);
            }
        }

        Set<String> survivingOsmNodeIds = new HashSet<>();
        for (Id<Node> nodeId : network.getNodes().keySet()) {
            String id = nodeId.toString();
            if (id.startsWith("osm_node_")) {
                survivingOsmNodeIds.add(id.substring("osm_node_".length()));
            }
        }
        routing.retainAll(survivingOsmNodeIds);
        classification.keySet().retainAll(survivingOsmNodeIds);
    }

    /**
     * Emits one merged {@link Link} plus its provenance, geometry and per-way index entries.
     *
     * <p>{@code traversalForward} is whether the first source segment is traversed nodeA->nodeB
     * (used only to pick the correct DIRECTIONAL routing attributes). Link id, {@code osm:wayId}
     * and the collapsed link's endpoints are canonicalized per physical span, so the two travel
     * directions of one merged link share the same ids/provenance.
     */
    private static void emitLink(Network network, String startOsm, String endOsm,
                                 boolean traversalForward, OsmSegmentGraph.Segment firstSegment,
                                 List<OsmLinkRef> sourceSegments, List<Coord> pts,
                                 List<String> chainNodeIds,
                                 OsmNetworkBuildConfig config,
                                 Map<String, OsmWayRecord> wayRecords, boolean rawTagsKept,
                                 boolean keepAllGeometryNodes,
                                 Map<String, OsmCollapsedLink> collapsedLinks,
                                 Map<String, List<String>> linkIdsByOsmWayId,
                                 Map<String, OsmPolyline> geometry,
                                 List<OsmImportIssue> issues) {

        double length = 0.0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double dx = pts.get(i).getX() - pts.get(i + 1).getX();
            double dy = pts.get(i).getY() - pts.get(i + 1).getY();
            length += Math.sqrt(dx * dx + dy * dy);
        }
        if (length <= 0.0) {
            return;
        }

        // Direction-independent canonical span: the two travel directions of one physical merged
        // link must derive the same link id and the same source-way provenance.
        String canonicalFrom = startOsm.compareTo(endOsm) <= 0 ? startOsm : endOsm;
        String canonicalTo = startOsm.equals(canonicalFrom) ? endOsm : startOsm;
        boolean forward = startOsm.equals(canonicalFrom);
        String firstWayId = (forward ? sourceSegments.get(0)
                : sourceSegments.get(sourceSegments.size() - 1)).osmWayId();

        // The emitted link is always from startOsm to endOsm and the first source segment is the one
        // incident to startOsm, so traversalForward already selects the attributes that apply to the
        // emitted from->to travel direction (nodeA->nodeB of the first segment when true). A segment
        // stored in the opposite orientation to the chain is thus inverted automatically.
        double lanes = firstSegment.lanes(traversalForward);
        double capacity = lanes * firstSegment.capacityPerLane();
        // MATERIALIZE keeps one link per atomic OSM segment (today's behavior); the contracted
        // modes use a deterministic id derived from the canonical physical span + first source way.
        String linkId = keepAllGeometryNodes
                ? OsmGeneratedIds.linkId(firstSegment.wayId(), firstSegment.segmentIndex(), traversalForward)
                : OsmGeneratedIds.simplifiedLinkId(firstWayId, forward, canonicalFrom, canonicalTo);
        if (!keepAllGeometryNodes && network.getLinks().containsKey(Id.create(linkId, Link.class))) {
            // Degenerate repeated-node span: a way that doubles back through a node referenced twice,
            // forcing that node to be kept. The canonical directed span is already represented, so do
            // not emit a parallel duplicate; the first occurrence keeps the provenance/geometry.
            // Record a deterministic WARNING rather than dropping the second physical span silently.
            issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "degenerate-span",
                    "Way " + firstWayId + " doubles back through a repeated node; dropping duplicate "
                            + "directed span " + linkId + " (already emitted)", null));
            return;
        }
        Set<String> linkModes = new TreeSet<>(firstSegment.modes(traversalForward));
        if (config.addBusToCarRoads() && linkModes.contains("car") && !linkModes.contains("bus")) {
            linkModes.add("bus");
        }
        Link link = network.createLink(linkId,
                OsmGeneratedIds.nodeId(startOsm), OsmGeneratedIds.nodeId(endOsm),
                length, capacity, firstSegment.speed(traversalForward), lanes,
                linkModes);

        link.getAttributes().putAttribute("osm:wayId", firstWayId);
        if (!keepAllGeometryNodes) {
            link.getAttributes().putAttribute("osm:simplified", "true");
        }
        link.getAttributes().putAttribute("osm:segmentCount", String.valueOf(sourceSegments.size()));

        // Full provenance: the merged link keeps its complete polyline and every source OSM way/node
        // so the contraction is reversible for inspection, GIS, and debugging. Geometry and node ids
        // are listed in the directed link's travel order (from -> to); source way ids follow the same
        // travel order. The representative name is derived from the CANONICAL first way so both travel
        // directions of one physical link report the same name.
        link.getAttributes().putAttribute("osm:geometry", toWkt(pts));
        link.getAttributes().putAttribute("osm:sourceNodes", String.join(",", chainNodeIds));
        // Source ways/names are listed in canonical span order (min endpoint first) so both travel
        // directions of one physical link report the same lists regardless of emitted direction.
        List<OsmLinkRef> canonicalSegments = new ArrayList<>(sourceSegments);
        if (!forward) {
            Collections.reverse(canonicalSegments);
        }
        List<String> sourceWayIds = distinctWayIds(canonicalSegments);
        link.getAttributes().putAttribute("osm:sourceWays", String.join(",", sourceWayIds));
        List<String> sourceNames = distinctWayNames(sourceWayIds, wayRecords);
        OsmWayRecord canonicalFirstWay = wayRecords.get(firstWayId);
        String representativeName = canonicalFirstWay == null ? null
                : canonicalFirstWay.tags().get("name");
        if (representativeName != null && representativeName.isBlank()) {
            representativeName = null;
        }
        if (representativeName == null && !sourceNames.isEmpty()) {
            representativeName = sourceNames.get(0);
        }
        if (representativeName != null) {
            link.getAttributes().putAttribute("osm:name", representativeName);
        }
        if (sourceNames.size() > 1) {
            link.getAttributes().putAttribute("osm:sourceNames", String.join(",", sourceNames));
        }

        // Rule and tags come from the canonical first source way; every segment in a contracted
        // chain shares routing attributes by construction of the selector, so it is representative.
        OsmWayRecord firstWay = wayRecords.get(firstWayId);
        if (firstWay != null) {
            OsmWayRule rule = config.resolveRule(firstWay.tags());
            if (rule != null) {
                link.getAttributes().putAttribute("osm:key", rule.key());
                link.getAttributes().putAttribute("osm:value", rule.value());
            }
            copyLaneTags(link, firstWay);
            if (rawTagsKept) {
                for (Map.Entry<String, String> e : firstWay.tags().asMap().entrySet()) {
                    link.getAttributes().putAttribute("osm:tag:" + e.getKey(), e.getValue());
                }
            }
        }

        OsmCollapsedLink collapsed = new OsmCollapsedLink(
                linkId, forward, canonicalFrom, canonicalTo, sourceSegments);
        collapsedLinks.put(linkId, collapsed);
        geometry.put(linkId, new OsmPolyline(pts));
        for (String wayId : collapsed.sourceOsmWayIds()) {
            List<String> ids = linkIdsByOsmWayId.computeIfAbsent(wayId, k -> new ArrayList<>());
            if (!ids.contains(linkId)) {
                ids.add(linkId);
            }
        }
    }

    private static String segmentKey(OsmSegmentGraph.Segment seg, boolean forward) {
        return seg.wayId() + "|" + seg.segmentIndex() + "|" + (forward ? "f" : "r");
    }

    /** WKT LINESTRING of the projected polyline; a single vertex degenerates to a POINT. */
    private static String toWkt(List<Coord> pts) {
        StringBuilder sb = new StringBuilder("LINESTRING (");
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(pts.get(i).getX()).append(' ').append(pts.get(i).getY());
        }
        return sb.append(')').toString();
    }

    /** Distinct source OSM way ids in travel order. */
    private static List<String> distinctWayIds(List<OsmLinkRef> sourceSegments) {
        List<String> ids = new ArrayList<>();
        for (OsmLinkRef r : sourceSegments) {
            if (!ids.contains(r.osmWayId())) {
                ids.add(r.osmWayId());
            }
        }
        return ids;
    }

    /** Distinct non-blank way names in travel order (empty when no source way is named). */
    private static List<String> distinctWayNames(List<String> wayIds, Map<String, OsmWayRecord> wayRecords) {
        List<String> names = new ArrayList<>();
        for (String wayId : wayIds) {
            OsmWayRecord way = wayRecords.get(wayId);
            if (way == null) {
                continue;
            }
            String name = way.tags().get("name");
            if (name != null && !name.isBlank() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** The unique other incident segment at a non-routing degree-2 pass-through node. */
    private static OsmSegmentGraph.Segment otherIncident(OsmSegmentGraph graph, String node,
                                                         OsmSegmentGraph.Segment used) {
        for (OsmSegmentGraph.Segment s : graph.segmentsFrom(node)) {
            if (s.wayId().equals(used.wayId()) && s.segmentIndex() == used.segmentIndex()) {
                continue;
            }
            return s;
        }
        return null;
    }

    /**
     * Retains every node of a restriction via-way chain (not just the via node), so a contracted
     * link cannot swallow the interior of a turn-restriction chain.
     */
    private static Set<String> viaNodes(OsmImportResult importResult) {
        Set<String> viaNodes = new TreeSet<>();
        for (OsmRelationRecord rel : importResult.relations().values()) {
            String type = rel.tags().get("type");
            if (type == null || !type.startsWith("restriction")) {
                continue;
            }
            for (OsmRelationMemberRecord mm : rel.members()) {
                if (!"via".equals(mm.role())) {
                    continue;
                }
                if (mm.type() == OsmElementType.NODE) {
                    viaNodes.add(mm.ref());
                } else if (mm.type() == OsmElementType.WAY) {
                    OsmWayRecord viaWay = importResult.ways().get(mm.ref());
                    if (viaWay != null) {
                        viaNodes.addAll(viaWay.nodeRefs());
                    }
                }
            }
        }
        return viaNodes;
    }

    private static Set<String> stopNodes(OsmImportResult importResult) {
        Set<String> stopNodes = new TreeSet<>();
        for (OsmNodeRecord nd : importResult.nodes().values()) {
            if (nd.tags().has("highway", "bus_stop") || nd.tags().has("highway", "tram_stop")
                    || nd.tags().get("public_transport") != null || nd.tags().get("railway") != null) {
                stopNodes.add(nd.id());
            }
        }
        return stopNodes;
    }

    /**
     * Anchors the smallest node of every connected component that has no routing node, so a purely
     * geometry-only cycle is not silently dropped.
     */
    private static Set<String> anchorCycles(OsmSegmentGraph graph, Set<String> routing) {
        Set<String> result = new TreeSet<>(routing);
        Set<String> seen = new HashSet<>();
        for (String nodeId : new TreeSet<>(graph.nodeIds())) {
            if (!seen.add(nodeId)) {
                continue;
            }
            List<String> component = new ArrayList<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(nodeId);
            while (!queue.isEmpty()) {
                String u = queue.poll();
                component.add(u);
                for (OsmSegmentGraph.Segment s : graph.segmentsFrom(u)) {
                    String v = graph.other(s, u);
                    if (seen.add(v)) {
                        queue.add(v);
                    }
                }
            }
            boolean hasRouting = false;
            for (String n : component) {
                if (result.contains(n)) {
                    hasRouting = true;
                    break;
                }
            }
            if (!hasRouting) {
                result.add(Collections.min(component));
            }
        }
        return result;
    }

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
}
