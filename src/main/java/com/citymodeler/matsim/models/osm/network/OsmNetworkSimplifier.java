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
     * Builds polylines for compact link creation.
     * For each segment between consecutive routing nodes, collects intermediate shape points.
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

            // Split way into segments between routing nodes
            List<Integer> routingIndices = new java.util.ArrayList<>();
            for (int i = 0; i < refs.size(); i++) {
                if (routingNodes.contains(refs.get(i))) {
                    routingIndices.add(i);
                }
            }
            if (routingIndices.size() < 2) continue;

            for (int s = 0; s + 1 < routingIndices.size(); s++) {
                int fromIdx = routingIndices.get(s);
                int toIdx = routingIndices.get(s + 1);

                // Forward and reverse
                List<Coord> forwardPoints = new java.util.ArrayList<>();
                for (int i = fromIdx; i <= toIdx; i++) {
                    OsmNodeRecord node = nodes.get(refs.get(i));
                    if (node != null) {
                        forwardPoints.add(node.projectedCoord());
                    }
                }
                if (forwardPoints.size() >= 2) {
                    String fwdLinkId = "osm_way_" + way.id() + "_" + s + "_f";
                    if (mode == OsmGeometryMode.PRESERVE_AS_LINK_GEOMETRY) {
                        geometry.put(fwdLinkId, new OsmPolyline(forwardPoints));
                    } else {
                        // ROUTING_NODES_ONLY: just start and end
                        geometry.put(fwdLinkId, new OsmPolyline(List.of(forwardPoints.get(0), forwardPoints.get(forwardPoints.size() - 1))));
                    }

                    List<Coord> reversePoints = new java.util.ArrayList<>();
                    for (int i = toIdx; i >= fromIdx; i--) {
                        OsmNodeRecord node = nodes.get(refs.get(i));
                        if (node != null) {
                            reversePoints.add(node.projectedCoord());
                        }
                    }
                    if (reversePoints.size() >= 2) {
                        String revLinkId = "osm_way_" + way.id() + "_" + s + "_r";
                        if (mode == OsmGeometryMode.PRESERVE_AS_LINK_GEOMETRY) {
                            geometry.put(revLinkId, new OsmPolyline(reversePoints));
                        } else {
                            geometry.put(revLinkId, new OsmPolyline(List.of(reversePoints.get(0), reversePoints.get(reversePoints.size() - 1))));
                        }
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
