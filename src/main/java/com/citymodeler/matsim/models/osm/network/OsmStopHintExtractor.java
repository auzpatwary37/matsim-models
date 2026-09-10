package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.osm.OsmElementType;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmStopHintExtractor {

    private OsmStopHintExtractor() {
    }

    public static List<OsmStopHint> extract(OsmImportResult importResult, OsmNetworkBuildResult buildResult) {
        List<OsmStopHint> hints = new ArrayList<>();

        // Check nodes
        for (OsmNodeRecord node : importResult.nodes().values()) {
            OsmStopHint hint = classifyNode(node, importResult);
            if (hint != null) {
                hints.add(hint);
            }
        }

        // Check ways (platforms, stations as ways)
        for (OsmWayRecord way : importResult.ways().values()) {
            OsmStopHint hint = classifyWay(way, importResult);
            if (hint != null) {
                hints.add(hint);
            }
        }

        return List.copyOf(hints);
    }

    private static OsmStopHint classifyNode(OsmNodeRecord node, OsmImportResult importResult) {
        OsmTagSet tags = node.tags();
        String publicTransport = tags.get("public_transport");
        String highway = tags.get("highway");
        String railway = tags.get("railway");
        String amenity = tags.get("amenity");
        String waterway = tags.get("waterway");

        OsmStopKind kind = null;
        Set<String> modes = new HashSet<>();

        if ("bus_stop".equals(highway)) {
            kind = OsmStopKind.BUS_STOP;
            modes.add("bus");
            modes.add("pt");
        } else if ("stop_position".equals(publicTransport)) {
            kind = OsmStopKind.STOP_POSITION;
            modes.addAll(resolveStopPositionModes(tags));
        } else if ("platform".equals(publicTransport)) {
            kind = OsmStopKind.PLATFORM;
            modes.addAll(resolveStopPositionModes(tags));
        } else if ("station".equals(railway)) {
            kind = OsmStopKind.RAILWAY_STATION;
            modes.add("rail");
            modes.add("pt");
        } else if ("halt".equals(railway)) {
            kind = OsmStopKind.RAILWAY_HALT;
            modes.add("rail");
            modes.add("pt");
        } else if ("tram_stop".equals(railway)) {
            kind = OsmStopKind.TRAM_STOP;
            modes.add("tram");
            modes.add("pt");
        } else if ("bus_station".equals(amenity)) {
            kind = OsmStopKind.BUS_STATION;
            modes.add("bus");
            modes.add("pt");
        } else if ("ferry_terminal".equals(amenity) || "ferry_terminal".equals(tags.get("maritime"))) {
            kind = OsmStopKind.FERRY_TERMINAL;
            modes.add("ferry");
        }

        if (kind == null) return null;

        List<String> parentRelIds = findParentRelations(node.id(), OsmElementType.NODE, importResult);
        List<String> nearbyLinks = findNearbyLinks(node.id(), buildResultSafe(importResult));

        return new OsmStopHint(
                node.id(),
                OsmElementType.NODE,
                kind,
                node.projectedCoord(),
                tags.get("name"),
                tags.get("ref"),
                tags.get("operator"),
                tags.get("network"),
                modes,
                parentRelIds,
                nearbyLinks);
    }

    private static OsmStopHint classifyWay(OsmWayRecord way, OsmImportResult importResult) {
        OsmTagSet tags = way.tags();
        String publicTransport = tags.get("public_transport");
        String railway = tags.get("railway");
        String highway = tags.get("highway");
        String amenity = tags.get("amenity");

        OsmStopKind kind = null;
        Set<String> modes = new HashSet<>();

        if ("stop_position".equals(publicTransport)) {
            kind = OsmStopKind.STOP_POSITION;
            modes.addAll(resolveStopPositionModes(tags));
        } else if ("platform".equals(publicTransport)) {
            kind = OsmStopKind.PLATFORM;
            modes.addAll(resolveStopPositionModes(tags));
        } else if ("station".equals(railway)) {
            kind = OsmStopKind.RAILWAY_STATION;
            modes.add("rail");
            modes.add("pt");
        } else if ("tram_stop".equals(railway)) {
            kind = OsmStopKind.TRAM_STOP;
            modes.add("tram");
            modes.add("pt");
        } else if ("bus_stop".equals(highway)) {
            kind = OsmStopKind.BUS_STOP;
            modes.add("bus");
            modes.add("pt");
        } else if ("bus_station".equals(amenity)) {
            kind = OsmStopKind.BUS_STATION;
            modes.add("bus");
            modes.add("pt");
        }

        if (kind == null) return null;

        // Use average of member node coords
        var nodeRecords = importResult.nodes();
        double avgX = 0, avgY = 0;
        int count = 0;
        for (String nodeId : way.nodeRefs()) {
            var node = nodeRecords.get(nodeId);
            if (node != null) {
                avgX += node.projectedCoord().getX();
                avgY += node.projectedCoord().getY();
                count++;
            }
        }
        var coord = count > 0
                ? new com.citymodeler.matsim.models.api.Coord(avgX / count, avgY / count)
                : new com.citymodeler.matsim.models.api.Coord(0, 0);

        List<String> parentRelIds = findParentRelations(way.id(), OsmElementType.WAY, importResult);

        return new OsmStopHint(
                way.id(),
                OsmElementType.WAY,
                kind,
                coord,
                tags.get("name"),
                tags.get("ref"),
                tags.get("operator"),
                tags.get("network"),
                modes,
                parentRelIds,
                List.of());
    }

    private static Set<String> resolveStopPositionModes(OsmTagSet tags) {
        Set<String> modes = new HashSet<>();
        if (isTruthy(tags.get("bus"))) modes.add("bus");
        if (isTruthy(tags.get("tram"))) modes.add("tram");
        if (isTruthy(tags.get("train"))) { modes.add("rail"); }
        if (isTruthy(tags.get("subway"))) { modes.add("subway"); }
        if (isTruthy(tags.get("ferry"))) { modes.add("ferry"); }
        if (isTruthy(tags.get("psv"))) { modes.add("pt"); }
        if (modes.isEmpty()) { modes.add("pt"); }
        return modes;
    }

    private static boolean isTruthy(String value) {
        return "yes".equals(value) || "designated".equals(value) || "permissive".equals(value) || "1".equals(value);
    }

    private static List<String> findParentRelations(String elementId, OsmElementType type, OsmImportResult importResult) {
        List<String> result = new ArrayList<>();
        for (OsmRelationRecord rel : importResult.relations().values()) {
            for (var member : rel.members()) {
                if (member.type() == type && elementId.equals(member.ref())) {
                    String relType = rel.tags().get("type");
                    if (relType != null && (relType.equals("route") || relType.equals("route_master")
                            || relType.equals("stop") || relType.equals("public_transport"))) {
                        result.add(rel.id());
                    }
                    break;
                }
            }
        }
        return result;
    }

    private static List<String> findNearbyLinks(String nodeId, OsmNetworkBuildResult ignored) {
        // v1: return empty; full proximity search comes with spatial index in Task 6
        return List.of();
    }

    private static OsmNetworkBuildResult buildResultSafe(OsmImportResult importResult) {
        return null;
    }
}
