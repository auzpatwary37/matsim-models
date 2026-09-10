package com.citymodeler.matsim.models.osm;

import java.util.Map;
import java.util.function.Predicate;

import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Filters OSM elements to a boundary polygon during import.
 * Nodes: point-in-polygon test.
 * Ways: included if ANY member node is inside the polygon (slightly over-inclusive, safe for routing).
 * Relations: included if ANY member is kept.
 */
public final class OsmPolygonFilter {

    private final OsmBoundary boundary;
    private final OsmBoundary.BBox bbox;

    public OsmPolygonFilter(OsmBoundary boundary) {
        this.boundary = boundary;
        this.bbox = boundary.boundingBox();
    }

    public boolean keepNode(OsmNodeRecord node) {
        return bbox.contains(node.lon(), node.lat()) && boundary.contains(node.lon(), node.lat());
    }

    /** Quick bbox check before full point-in-poly (useful for early rejection). */
    public boolean bboxContains(double lon, double lat) {
        return bbox.contains(lon, lat);
    }

    public boolean keepWay(OsmWayRecord way, Map<String, OsmNodeRecord> nodes) {
        for (String nodeId : way.nodeRefs()) {
            OsmNodeRecord node = nodes.get(nodeId);
            if (node == null) continue;
            if (bboxContains(node.lon(), node.lat()) && boundary.contains(node.lon(), node.lat())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the way has at least one node inside the polygon,
     * OR if any segment between consecutive nodes crosses the polygon boundary.
     * Uses the original (unfiltered) node map.
     */
    public boolean wayIntersectsBoundary(java.util.List<String> nodeIds, Map<String, OsmNodeRecord> allNodes) {
        // Fast path: any node inside?
        for (String nodeId : nodeIds) {
            OsmNodeRecord node = allNodes.get(nodeId);
            if (node == null) continue;
            if (bboxContains(node.lon(), node.lat()) && boundary.contains(node.lon(), node.lat())) {
                return true;
            }
        }
        // Segment crossing check: look for a segment where one endpoint is
        // inside and the other is outside (or both outside but segment passes through).
        // For efficiency, only check segments where at least one endpoint is in the bbox.
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            OsmNodeRecord a = allNodes.get(nodeIds.get(i));
            OsmNodeRecord b = allNodes.get(nodeIds.get(i + 1));
            if (a == null || b == null) continue;
            boolean aInside = boundary.contains(a.lon(), a.lat());
            boolean bInside = boundary.contains(b.lon(), b.lat());
            if (aInside != bInside) {
                return true; // segment crosses boundary
            }
            // Both outside: check if segment midpoint is inside (heuristic for
            // segments that arc through the polygon without endpoints inside).
            if (!aInside && !bInside) {
                double midLon = (a.lon() + b.lon()) / 2.0;
                double midLat = (a.lat() + b.lat()) / 2.0;
                if (bboxContains(midLon, midLat) && boundary.contains(midLon, midLat)) {
                    return true;
                }
            }
        }
        return false;
    }

    public OsmBoundary boundary() {
        return boundary;
    }
}
