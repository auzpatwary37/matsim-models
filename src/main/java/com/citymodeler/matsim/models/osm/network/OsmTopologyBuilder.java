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
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmElementType;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmImportResult;
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
        return buildCore(importResult, config, null, keepAllGeometryNodes, false);
    }

    /**
     * Signal-ready contraction: additionally retains transit-stop nodes and via nodes, and honors
     * the simplification options' sharp-bend / explicit-preserve policy. Always contracts
     * geometry-only nodes (that is its purpose).
     */
    public static CollapsedTopology buildSignalReady(OsmImportResult importResult,
                                                     OsmNetworkBuildConfig config,
                                                     OsmSimplifyOptions options) {
        return buildCore(importResult, config, options, false, true);
    }

    private static CollapsedTopology buildCore(OsmImportResult importResult,
                                               OsmNetworkBuildConfig config,
                                               OsmSimplifyOptions options,
                                               boolean keepAllGeometryNodes,
                                               boolean signalReady) {
        List<OsmImportIssue> issues = new ArrayList<>(importResult.issues());

        Set<String> acceptedWayIds = new TreeSet<>();
        for (OsmWayRecord w : importResult.ways().values()) {
            if (config.resolveRule(w.tags()) != null && w.nodeRefs().size() >= 2) {
                acceptedWayIds.add(w.id());
            }
        }

        OsmSegmentGraph graph = OsmSegmentGraph.build(importResult, config);

        Set<String> stopNodes = signalReady ? stopNodes(importResult) : new TreeSet<>();
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
                config.preserveCrossingNodes());

        Set<String> routing = OsmRoutingNodeSelector.select(graph, classification, keepAllGeometryNodes);
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
                OsmNodeRecord startRec = nodeRecords.get(start);
                if (startRec == null) {
                    continue;
                }
                pts.add(startRec.projectedCoord());

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
                emitLink(network, start, end, chainForward, firstSeg, sourceSegments, pts,
                        config, wayRecords, rawTagsKept, keepAllGeometryNodes,
                        collapsedLinks, linkIdsByOsmWayId, geometry);
            }
        }

        network.postProcess();

        return new CollapsedTopology(network, collapsedLinks, linkIdsByOsmWayId, geometry,
                classification, new TreeSet<>(routing), issues);
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
                                 OsmNetworkBuildConfig config,
                                 Map<String, OsmWayRecord> wayRecords, boolean rawTagsKept,
                                 boolean keepAllGeometryNodes,
                                 Map<String, OsmCollapsedLink> collapsedLinks,
                                 Map<String, List<String>> linkIdsByOsmWayId,
                                 Map<String, OsmPolyline> geometry) {

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

        double lanes = firstSegment.lanes(traversalForward);
        double capacity = lanes * firstSegment.capacityPerLane();
        // MATERIALIZE keeps one link per atomic OSM segment (today's behavior); the contracted
        // modes use a deterministic id derived from the canonical physical span + first source way.
        String linkId = keepAllGeometryNodes
                ? OsmGeneratedIds.linkId(firstSegment.wayId(), firstSegment.segmentIndex(), traversalForward)
                : OsmGeneratedIds.simplifiedLinkId(firstWayId, forward, canonicalFrom, canonicalTo);
        Link link = network.createLink(linkId,
                OsmGeneratedIds.nodeId(startOsm), OsmGeneratedIds.nodeId(endOsm),
                length, capacity, firstSegment.speed(traversalForward), lanes,
                firstSegment.modes(traversalForward));

        link.getAttributes().putAttribute("osm:wayId", firstWayId);
        if (!keepAllGeometryNodes) {
            link.getAttributes().putAttribute("osm:simplified", "true");
        }
        link.getAttributes().putAttribute("osm:segmentCount", String.valueOf(sourceSegments.size()));

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
