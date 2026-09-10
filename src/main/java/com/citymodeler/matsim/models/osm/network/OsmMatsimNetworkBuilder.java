package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmMatsimNetworkBuilder {

    private final OsmModeAccessResolver accessResolver = new OsmModeAccessResolver();
    private final OsmSpeedResolver speedResolver = new OsmSpeedResolver();
    private final OsmLaneResolver laneResolver = new OsmLaneResolver();

    public OsmNetworkBuildResult build(OsmImportResult importResult, OsmNetworkBuildConfig config) {
        Network network = new Network("osm-network");
        List<OsmImportIssue> issues = new ArrayList<>(importResult.issues());
        Map<String, OsmLinkRef> linkRefsByLinkId = new LinkedHashMap<>();
        Map<String, List<String>> linkIdsByOsmWayId = new LinkedHashMap<>();

        // Collect node ids referenced by accepted ways to avoid materializing
        // unrelated POI/building nodes into the network.
        java.util.Set<String> acceptedNodeIds = new java.util.LinkedHashSet<>();
        for (OsmWayRecord way : importResult.ways().values()) {
            if (config.resolveRule(way.tags()) != null && !way.nodeRefs().isEmpty()) {
                acceptedNodeIds.addAll(way.nodeRefs());
            }
        }

        for (OsmNodeRecord nodeRecord : importResult.nodes().values()) {
            if (!acceptedNodeIds.contains(nodeRecord.id())) {
                continue;
            }
            String networkNodeId = OsmGeneratedIds.nodeId(nodeRecord.id());
            network.createNode(networkNodeId,
                    nodeRecord.projectedCoord().getX(),
                    nodeRecord.projectedCoord().getY());
        }

        for (OsmWayRecord way : importResult.ways().values()) {
            OsmWayRule rule = config.resolveRule(way.tags());
            if (rule == null) {
                continue;
            }

            if (OsmModeAccessResolver.hasDynamicOneway(way.tags())) {
                issues.add(new OsmImportIssue(
                        OsmIssueSeverity.WARNING,
                        "dynamic-oneway",
                        "Way " + way.id() + " has oneway=" + way.tags().get("oneway")
                                + "; treating as bidirectional (static importer policy)",
                        null));
            }

            List<OsmModeAccessResolver.DirectionDecision> decisions = accessResolver.resolve(way, rule.allowedModes());
            if (decisions.isEmpty()) {
                continue;
            }

            List<String> nodeRefs = way.nodeRefs();
            if (nodeRefs.size() < 2) {
                continue;
            }

            List<String> wayLinkIds = new ArrayList<>();
            boolean bidirectionalDecided = false;

            for (OsmModeAccessResolver.DirectionDecision decision : decisions) {
                if (decision.allowedModes().isEmpty()) {
                    continue;
                }

                boolean bidirectional = decision.forward() && decision.backward();
                if (bidirectional) {
                    bidirectionalDecided = true;
                }

                if (decision.forward()) {
                    for (String linkId : createSegments(importResult, network, way, rule,
                            nodeRefs, decision.allowedModes(), true, !bidirectional, linkRefsByLinkId, issues)) {
                        wayLinkIds.add(linkId);
                    }
                }
                if (decision.backward()) {
                    for (String linkId : createSegments(importResult, network, way, rule,
                            nodeRefs, decision.allowedModes(), false, !bidirectional, linkRefsByLinkId, issues)) {
                        wayLinkIds.add(linkId);
                    }
                }
            }

            if (!wayLinkIds.isEmpty()) {
                linkIdsByOsmWayId.put(way.id(), List.copyOf(wayLinkIds));
            }
        }

        network.postProcess();
        importResult.applyProvenanceTo(network);

        var baseResult = new OsmNetworkBuildResult(network, issues, linkRefsByLinkId, linkIdsByOsmWayId);

        List<OsmStopHint> stopHints = OsmStopHintExtractor.extract(importResult, baseResult);
        Map<String, OsmLaneHint> laneHints = OsmLaneHintExtractor.extractLaneHints(importResult, baseResult);
        Map<String, OsmIntersectionLaneHint> intersectionHints =
                OsmLaneHintExtractor.extractIntersectionLaneHints(importResult, network, laneHints);

        return new OsmNetworkBuildResult(network, issues, linkRefsByLinkId, linkIdsByOsmWayId,
                stopHints, laneHints, intersectionHints);
    }

    private List<String> createSegments(
            OsmImportResult importResult,
            Network network,
            OsmWayRecord way,
            OsmWayRule rule,
            List<String> nodeRefs,
            Set<String> modes,
            boolean forward,
            boolean oneway,
            Map<String, OsmLinkRef> linkRefs,
            List<OsmImportIssue> issues) {

        List<String> linkIds = new ArrayList<>();
        for (int i = 0; i < nodeRefs.size() - 1; i++) {
            String fromOsmId = forward ? nodeRefs.get(i) : nodeRefs.get(i + 1);
            String toOsmId = forward ? nodeRefs.get(i + 1) : nodeRefs.get(i);
            String fromNodeId = OsmGeneratedIds.nodeId(fromOsmId);
            String toNodeId = OsmGeneratedIds.nodeId(toOsmId);

            OsmNodeRecord fromRecord = importResult.nodes().get(fromOsmId);
            OsmNodeRecord toRecord = importResult.nodes().get(toOsmId);
            if (fromRecord == null || toRecord == null) {
                issues.add(new OsmImportIssue(
                        OsmIssueSeverity.WARNING,
                        "missing-node",
                        "Way " + way.id() + " references missing node",
                        null));
                continue;
            }

            double dx = fromRecord.projectedCoord().getX() - toRecord.projectedCoord().getX();
            double dy = fromRecord.projectedCoord().getY() - toRecord.projectedCoord().getY();
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length <= 0.0) {
                continue;
            }

            double speed = speedResolver.resolve(way, rule, forward);
            double lanes = laneResolver.resolve(way, rule, forward, oneway);
            double capacity = lanes * rule.capacityPerLane();

            String linkId = OsmGeneratedIds.linkId(way.id(), i, forward);

            Link link = network.createLink(
                    linkId, fromNodeId, toNodeId,
                    length, capacity, speed, lanes, modes);

            link.getAttributes().putAttribute("osm:wayId", way.id());
            link.getAttributes().putAttribute("osm:key", rule.key());
            link.getAttributes().putAttribute("osm:value", rule.value());
            if (importResult.provenance().rawTagsKept()) {
                for (Map.Entry<String, String> entry : way.tags().asMap().entrySet()) {
                    link.getAttributes().putAttribute("osm:tag:" + entry.getKey(), entry.getValue());
                }
            }

            linkRefs.put(linkId, new OsmLinkRef(linkId, way.id(), i, forward));
            linkIds.add(linkId);
        }
        return linkIds;
    }
}
