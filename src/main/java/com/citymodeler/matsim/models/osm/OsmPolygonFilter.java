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

    public OsmBoundary boundary() {
        return boundary;
    }
}
