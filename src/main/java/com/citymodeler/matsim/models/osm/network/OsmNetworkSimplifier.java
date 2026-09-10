package com.citymodeler.matsim.models.osm.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Determines which OSM nodes are "routing nodes" (kept as network nodes)
 * vs "shape-only" nodes (stored as intermediate geometry points).
 */
public final class OsmNetworkSimplifier {

    private OsmNetworkSimplifier() {
    }

    /**
     * Computes the set of retained (routing) node IDs for the given ways.
     */
    public static Set<String> computeRoutingNodes(
            List<OsmWayRecord> ways,
            Map<String, OsmNodeRecord> nodes,
            OsmNetworkBuildConfig config,
            Set<String> stopNodeIds) {

        if (config.geometryMode() == OsmGeometryMode.MATERIALIZE_GEOMETRY_NODES) {
            Set<String> all = new HashSet<>();
            for (OsmWayRecord way : ways) {
                all.addAll(way.nodeRefs());
            }
            return all;
        }

        // Count how many converted ways reference each node
        Map<String, Integer> refCount = new HashMap<>();
        for (OsmWayRecord way : ways) {
            Set<String> unique = new HashSet<>(way.nodeRefs());
            for (String nodeId : unique) {
                refCount.merge(nodeId, 1, Integer::sum);
            }
        }

        Set<String> routingNodes = new HashSet<>();
        for (OsmWayRecord way : ways) {
            List<String> refs = way.nodeRefs();
            // Always keep first and last
            routingNodes.add(refs.get(0));
            routingNodes.add(refs.get(refs.size() - 1));

            // Keep nodes shared by multiple ways (intersections)
            for (String nodeId : refs) {
                if (refCount.getOrDefault(nodeId, 0) > 1) {
                    routingNodes.add(nodeId);
                }
            }
        }

        // Keep explicitly configured nodes
        routingNodes.addAll(config.explicitOsmNodeIdsToKeep());

        // Keep transit stop nodes
        if (config.preserveTransitStopNodes()) {
            routingNodes.addAll(stopNodeIds);
        }

        return routingNodes;
    }

    /**
     * Builds polylines keyed by network link ID (matching the builder's per-segment indexing).
     *
     * <p><b>Note:</b> All three geometry modes currently produce the same network topology
     * (one link per consecutive OSM node pair). The geometry store provides sidecar polylines
     * for potential downstream use (e.g., rendering, shape matching) without altering link IDs.
     * True routing-node collapsing (where shape nodes are absorbed into fewer links) is
     * deferred to a future phase.
     */
    public static Map<String, OsmPolyline> buildGeometry(
            List<OsmWayRecord> ways,
            Map<String, OsmNodeRecord> nodes,
            Set<String> routingNodes,
            OsmGeometryMode mode,
            OsmNetworkBuildConfig config) {

        if (mode == OsmGeometryMode.MATERIALIZE_GEOMETRY_NODES) {
            return Map.of(); // No sidecar needed; all nodes are materialized
        }

        Map<String, OsmPolyline> geometry = new HashMap<>();

        for (OsmWayRecord way : ways) {
            List<String> refs = way.nodeRefs();

            // Use the SAME segment index as the network builder (consecutive node pairs)
            for (int i = 0; i + 1 < refs.size(); i++) {
                OsmNodeRecord fromNode = nodes.get(refs.get(i));
                OsmNodeRecord toNode = nodes.get(refs.get(i + 1));
                if (fromNode == null || toNode == null) continue;

                String fwdLinkId = OsmGeneratedIds.linkId(way.id(), i, true);
                String revLinkId = OsmGeneratedIds.linkId(way.id(), i, false);

                // Collect all shape points between this segment's routing endpoints
                // (for multi-segment stretches between routing nodes, store the full polyline
                //  under the first segment's ID as a "span" — but for Phase 1, store per-segment)
                List<Coord> forwardPoints = List.of(fromNode.projectedCoord(), toNode.projectedCoord());
                List<Coord> reversePoints = List.of(toNode.projectedCoord(), fromNode.projectedCoord());

                if (mode == OsmGeometryMode.PRESERVE_AS_LINK_GEOMETRY) {
                    geometry.put(fwdLinkId, new OsmPolyline(forwardPoints));
                    geometry.put(revLinkId, new OsmPolyline(reversePoints));
                } else {
                    // ROUTING_NODES_ONLY: only store if both endpoints are routing nodes
                    if (routingNodes.contains(refs.get(i)) && routingNodes.contains(refs.get(i + 1))) {
                        geometry.put(fwdLinkId, new OsmPolyline(forwardPoints));
                        geometry.put(revLinkId, new OsmPolyline(reversePoints));
                    }
                }
            }
        }
        return geometry;
    }

    /**
     * For a given way, returns the list of segment ranges (fromIdx, toIdx)
     * where each segment is between two consecutive routing nodes.
     */
    public static List<int[]> segmentRanges(List<String> nodeRefs, Set<String> routingNodes) {
        List<int[]> ranges = new java.util.ArrayList<>();
        int lastRoutingIdx = -1;
        for (int i = 0; i < nodeRefs.size(); i++) {
            if (routingNodes.contains(nodeRefs.get(i))) {
                if (lastRoutingIdx >= 0) {
                    ranges.add(new int[]{lastRoutingIdx, i});
                }
                lastRoutingIdx = i;
            }
        }
        return ranges;
    }
}
