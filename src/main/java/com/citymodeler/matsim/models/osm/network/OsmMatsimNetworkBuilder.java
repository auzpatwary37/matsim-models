package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmMatsimNetworkBuilder {

    public OsmNetworkBuildResult build(OsmImportResult importResult, OsmNetworkBuildConfig config) {
        boolean keepAllGeometryNodes = config.geometryMode() == OsmGeometryMode.MATERIALIZE_GEOMETRY_NODES;

        CollapsedTopology collapsed = OsmTopologyBuilder.build(importResult, config, keepAllGeometryNodes);
        Network network = collapsed.network();

        List<OsmImportIssue> issues = new ArrayList<>(collapsed.issues());
        addWayWarnings(importResult, config, issues);

        // Every emitted network link maps to a representative source segment (the first in travel
        // order), re-keyed to the network link id. This works in every geometry mode and lets a
        // consumer resolve a real link back to the OSM way and travel direction that produced it.
        Map<String, OsmLinkRef> linkRefsByLinkId = new LinkedHashMap<>();
        for (Map.Entry<String, OsmCollapsedLink> entry : collapsed.collapsedLinksByLinkId().entrySet()) {
            OsmLinkRef source = entry.getValue().sourceSegments().get(0);
            linkRefsByLinkId.put(entry.getKey(), new OsmLinkRef(
                    entry.getKey(), source.osmWayId(), source.segmentIndex(), source.forward()));
        }
        Map<String, List<String>> linkIdsByOsmWayId = collapsed.linkIdsByOsmWayId();

        importResult.applyProvenanceTo(network);

        var baseResult = new OsmNetworkBuildResult(network, issues, linkRefsByLinkId, linkIdsByOsmWayId);

        List<OsmStopHint> stopHints = OsmStopHintExtractor.extract(importResult, baseResult);
        Map<String, OsmLaneHint> laneHints = OsmLaneHintExtractor.extractLaneHints(importResult, baseResult);
        Map<String, OsmIntersectionLaneHint> intersectionHints =
                OsmLaneHintExtractor.extractIntersectionLaneHints(importResult, network, laneHints);

        OsmGeometryStore geometryStore = keepAllGeometryNodes
                ? OsmGeometryStore.empty()
                : new OsmGeometryStore(collapsed.geometry());

        return new OsmNetworkBuildResult(network, issues, linkRefsByLinkId, linkIdsByOsmWayId,
                stopHints, laneHints, intersectionHints, geometryStore);
    }

    /**
     * Importer-policy warnings that are not part of the contraction engine: dynamic oneway values
     * are treated as bidirectional, and node references that do not resolve are dropped.
     *
     * <p>Emitted per accepted way in sorted order so output stays deterministic.
     */
    private static void addWayWarnings(OsmImportResult importResult, OsmNetworkBuildConfig config,
                                       List<OsmImportIssue> issues) {
        OsmModeAccessResolver accessResolver = new OsmModeAccessResolver();
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
            if (way.nodeRefs().size() < 2
                    || accessResolver.resolve(way, rule.allowedModes()).isEmpty()) {
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (String nodeRef : way.nodeRefs()) {
                if (importResult.nodes().get(nodeRef) == null && !missing.contains(nodeRef)) {
                    missing.add(nodeRef);
                }
            }
            for (String nodeRef : missing) {
                issues.add(new OsmImportIssue(
                        OsmIssueSeverity.WARNING,
                        "missing-node",
                        "Way " + way.id() + " references missing node " + nodeRef,
                        null));
            }
        }
    }

    /**
     * Build the network and then run signal-aware simplification, returning a collapsed network with
     * signalized-junction metadata, re-attached turn restrictions, and a signal-readiness report.
     */
    public OsmSimplifiedNetwork buildSignalReady(OsmImportResult importResult, OsmNetworkBuildConfig config) {
        OsmNetworkBuildResult built = build(importResult, config);
        return OsmSignalAwareSimplifier.simplify(built, importResult, config, OsmSimplifyOptions.from(config));
    }
}
