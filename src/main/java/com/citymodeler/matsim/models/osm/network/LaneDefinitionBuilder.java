package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Builds a {@link Lanes} model for every link of an emitted network.
 *
 * <p>A bare {@code turn:lanes} on a bidirectional way is applied only left→right in the travel
 * direction (the directional {@code turn:lanes:forward}/{@code :backward} tag always takes
 * precedence); the decomposer's count/cell reconciliation (Task 3) is the guard against a bad
 * alignment.
 */
public final class LaneDefinitionBuilder {

    private final OsmDirectionalLaneResolver countResolver = new OsmDirectionalLaneResolver();
    private final OsmTurnLaneParser turnParser = new OsmTurnLaneParser();
    private final OsmLaneDecomposer decomposer = new OsmLaneDecomposer();

    public LaneDefinitionResult build(Network network, Map<String, OsmLinkRef> refs,
                                      Map<String, OsmWayRecord> ways, OsmGeometryStore geometryStore,
                                      OsmNetworkBuildConfig config) {
        Lanes lanes = new Lanes();
        List<OsmImportIssue> issues = new ArrayList<>();
        MovementTurnClassifier classifier = new GeometryTurnClassifier(network, geometryStore);

        Map<String, List<String>> outgoingByNode = new TreeMap<>();
        for (Link link : network.getLinks().values()) {
            outgoingByNode.computeIfAbsent(link.getFromNodeId().toString(), k -> new ArrayList<>())
                    .add(link.getId().toString());
        }
        outgoingByNode.values().forEach(list -> list.sort(String::compareTo));

        for (String linkId : new TreeMap<>(refs).keySet()) {
            Link link = network.getLinks().get(Id.create(linkId, Link.class));
            OsmLinkRef ref = refs.get(linkId);
            OsmWayRecord way = ref == null ? null : ways.get(ref.osmWayId());
            if (link == null || ref == null || way == null) {
                // Cannot resolve lanes without both the emitted link and its source OSM way; skipping
                // silently would hide a link losing its lane assignment from the bundle report.
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "lane-unresolved-way",
                        "Link " + linkId + " has no resolvable source way; no lane assignment", null));
                continue;
            }
            OsmWayRule rule = config.resolveRule(way.tags());
            if (rule == null) {
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "lane-unresolved-way",
                        "Link " + linkId + " (way " + ref.osmWayId()
                                + ") has no matching network rule; no lane assignment", null));
                continue;
            }
            boolean oneway = isOneway(way);
            OsmLaneCount count = countResolver.resolve(way.tags(), ref.forward(), oneway);
            List<OsmTurnLaneCell> cells = turnParser.parse(turnLanes(way, ref.forward()));
            List<String> outgoing = outgoingByNode.getOrDefault(
                    link.getToNodeId().toString(), List.of());
            if (outgoing.isEmpty()) {
                // A lane must carry at least one leadsTo (XSD-mandatory); a link whose to-node has no
                // outgoing links (dead end / isolated) cannot produce a valid assignment. Skip it with
                // a visible issue rather than fabricating a movement or emitting an invalid lane.
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "lane-no-outgoing",
                        "Link " + linkId + " has no outgoing links at its to-node; no lane assignment",
                        null));
                continue;
            }

            LaneDecomposition decomposition = decomposer.decompose(linkId, count, cells, outgoing,
                    classifier, rule.capacityPerLane());
            lanes.addAssignment(decomposition.assignment());
            issues.addAll(decomposition.issues());
        }
        return new LaneDefinitionResult(lanes, issues);
    }

    private static boolean isOneway(OsmWayRecord way) {
        String oneway = way.tags().get("oneway");
        // oneway=-1 means the draw direction is reversed but it is still one-way, so lane counts
        // (which describe the travelled direction) apply to a single direction.
        return "yes".equals(oneway) || "1".equals(oneway) || "true".equals(oneway)
                || "-1".equals(oneway);
    }

    private static String turnLanes(OsmWayRecord way, boolean forward) {
        String directional = way.tags().get("turn:lanes" + (forward ? ":forward" : ":backward"));
        if (directional != null && !directional.isBlank()) {
            return directional;
        }
        // No directional tag for this travel direction: fall back to the bare turn:lanes. A bare tag
        // on a bidirectional way is applied left→right in each travel direction; the decomposer's
        // count/cell reconciliation (Task 3) is the guard against a bad alignment.
        return way.tags().get("turn:lanes");
    }
}
