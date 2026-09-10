package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmLaneHintExtractor {

    private OsmLaneHintExtractor() {
    }

    public static Map<String, OsmLaneHint> extractLaneHints(
            OsmImportResult importResult, OsmNetworkBuildResult buildResult) {

        Map<String, OsmLaneHint> hints = new HashMap<>();
        OsmNetworkBuildConfig config = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmLaneResolver laneResolver = new OsmLaneResolver();
        OsmModeAccessResolver accessResolver = new OsmModeAccessResolver();

        for (Map.Entry<String, OsmWayRecord> entry : importResult.ways().entrySet()) {
            OsmWayRecord way = entry.getValue();
            OsmWayRule rule = config.resolveRule(way.tags());
            if (rule == null) continue;

            List<OsmModeAccessResolver.DirectionDecision> decisions =
                    accessResolver.resolve(way, rule.allowedModes());
            for (OsmModeAccessResolver.DirectionDecision decision : decisions) {
                boolean forward = decision.forward();
                if (!forward && !decision.backward()) continue;

                // Find the link ID for this direction
                String dirSuffix = forward ? "_f" : "_r";
                for (int segIdx = 0; segIdx < way.nodeRefs().size() - 1; segIdx++) {
                    String linkId = "osm_way_" + way.id() + "_" + segIdx + dirSuffix;
                    if (!buildResult.cleanedNetwork().getLinks().containsKey(
                            com.citymodeler.matsim.models.api.Id.create(linkId, Link.class))) {
                        continue;
                    }

                    double totalLanes = laneResolver.resolve(way, rule, forward, !decision.backward());
                    double busLanes = resolveDedicatedLanes(way.tags(), "bus", forward);
                    double psvLanes = resolveDedicatedLanes(way.tags(), "psv", forward);
                    String turnLanes = resolveTurnLanes(way.tags(), forward);
                    boolean dedicated = busLanes > 0 || psvLanes > 0 || hasDesignatedPattern(way.tags(), forward);
                    Set<String> modes = decision.allowedModes();

                    hints.put(linkId, new OsmLaneHint(
                            linkId, way.id(), forward,
                            totalLanes, busLanes, psvLanes,
                            turnLanes, dedicated, modes));
                }
            }
        }
        return hints;
    }

    public static Map<String, OsmIntersectionLaneHint> extractIntersectionLaneHints(
            OsmImportResult importResult, Network network, Map<String, OsmLaneHint> laneHints) {

        Map<String, OsmIntersectionLaneHint> result = new HashMap<>();

        for (var entry : network.getNodes().entrySet()) {
            String nodeId = entry.getKey().toString();
            var node = entry.getValue();
            var inLinks = node.getInLinks().values();
            var outLinks = node.getOutLinks().values();
            if (inLinks.isEmpty() && outLinks.isEmpty()) continue;

            List<String> incomingIds = new ArrayList<>();
            List<String> outgoingIds = new ArrayList<>();
            Map<String, Double> approachLanes = new HashMap<>();
            Map<String, String> turnLanes = new HashMap<>();

            for (Link link : inLinks) {
                String lid = link.getId().toString();
                incomingIds.add(lid);
                OsmLaneHint hint = laneHints.get(lid);
                if (hint != null) {
                    approachLanes.put(lid, hint.totalLanes());
                    if (hint.turnLanes() != null) {
                        turnLanes.put(lid, hint.turnLanes());
                    }
                } else {
                    approachLanes.put(lid, link.getNumberOfLanes());
                }
            }
            for (Link link : outLinks) {
                outgoingIds.add(link.getId().toString());
            }

            // Check traffic signal from source OSM node
            boolean trafficSignal = false;
            String osmNodeId = nodeId.startsWith("osm_node_") ? nodeId.substring(9) : nodeId;
            OsmNodeRecord osmNode = importResult.nodes().get(osmNodeId);
            if (osmNode != null && "traffic_signals".equals(osmNode.tags().get("highway"))) {
                trafficSignal = true;
            }

            result.put(nodeId, new OsmIntersectionLaneHint(
                    nodeId, incomingIds, outgoingIds, approachLanes, turnLanes, trafficSignal));
        }
        return result;
    }

    private static double resolveDedicatedLanes(OsmTagSet tags, String mode, boolean forward) {
        String dirKey = mode + ":lanes" + (forward ? ":forward" : ":backward");
        String baseKey = mode + ":lanes";
        String value = tags.get(dirKey);
        if (value == null || value.isBlank()) {
            value = tags.get(baseKey);
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException ignored) {
            }
            // Check pipe-delimited pattern: "designated|none|none"
            long designated = value.chars().filter(c -> c == '|').count() + 1;
            String[] parts = value.split("\\|");
            long count = 0;
            for (String p : parts) {
                if ("designated".equals(p.trim()) || "yes".equals(p.trim())) count++;
            }
            return count;
        }
        return 0.0;
    }

    private static String resolveTurnLanes(OsmTagSet tags, boolean forward) {
        String dirKey = "turn:lanes" + (forward ? ":forward" : ":backward");
        String value = tags.get(dirKey);
        if (value != null && !value.isBlank()) return value;
        value = tags.get("turn:lanes");
        return (value != null && !value.isBlank()) ? value : null;
    }

    private static boolean hasDesignatedPattern(OsmTagSet tags, boolean forward) {
        for (String prefix : List.of("bus", "psv")) {
            String key = prefix + (forward ? ":forward" : ":backward");
            String val = tags.get(key);
            if ("designated".equals(val)) return true;
            val = tags.get(prefix);
            if ("designated".equals(val)) return true;
        }
        return false;
    }
}
