package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.citymodeler.matsim.models.api.Id;
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

        Map<String, OsmLaneHint> hints = new TreeMap<>();
        OsmNetworkBuildConfig config = OsmNetworkBuildConfig.defaultConfig();
        OsmLaneResolver laneResolver = new OsmLaneResolver();
        OsmModeAccessResolver accessResolver = new OsmModeAccessResolver();

        // Key off the ACTUAL emitted network links (the build result maps every network link id to a
        // representative source segment), so hints work in every geometry mode: contracted modes
        // emit sim_* merged links, MATERIALIZE emits per-segment links.
        for (Map.Entry<String, OsmLinkRef> entry : buildResult.linkRefsByLinkId().entrySet()) {
            String linkId = entry.getKey();
            OsmLinkRef ref = entry.getValue();

            Link link = buildResult.cleanedNetwork().getLinks().get(Id.create(linkId, Link.class));
            if (link == null) {
                continue;
            }
            OsmWayRecord way = importResult.ways().get(ref.osmWayId());
            if (way == null) {
                continue;
            }
            OsmWayRule rule = config.resolveRule(way.tags());
            if (rule == null) {
                continue;
            }

            boolean forward = ref.forward();

            // A direction is "oneway" for lane-resolution purposes unless the way has a single
            // decision granting BOTH directions (matching the resolver call the old extractor made).
            boolean anyAllowed = false;
            boolean bidirectional = false;
            for (OsmModeAccessResolver.DirectionDecision decision
                    : accessResolver.resolve(way, rule.allowedModes())) {
                if (decision.allowedModes().isEmpty()) {
                    continue;
                }
                anyAllowed = true;
                bidirectional |= decision.forward() && decision.backward();
            }
            if (!anyAllowed) {
                continue;
            }
            boolean oneway = !bidirectional;

            double totalLanes = laneResolver.resolve(way, rule, forward, oneway);
            double busLanes = resolveDedicatedLanes(way.tags(), "bus", forward);
            double psvLanes = resolveDedicatedLanes(way.tags(), "psv", forward);
            String turnLanes = resolveTurnLanes(way.tags(), forward);
            boolean dedicated = busLanes > 0 || psvLanes > 0 || hasDesignatedPattern(way.tags(), forward);
            Set<String> modes = link.getAllowedModes();

            hints.put(linkId, new OsmLaneHint(
                    linkId, way.id(), forward,
                    totalLanes, busLanes, psvLanes,
                    turnLanes, dedicated, modes));
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
