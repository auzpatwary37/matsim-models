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
 * <p>A bare {@code turn:lanes} on a bidirectional way is applied only when an even directional split
 * is resolved (or the way is one-way), left→right in the travel direction; a directional
 * {@code turn:lanes:forward}/{@code :backward} tag always takes precedence. An unorientable bare tag
 * is omitted and reported as {@code ambiguous-turn-lanes}.
 */
public final class LaneDefinitionBuilder {

    private final OsmDirectionalLaneResolver countResolver = new OsmDirectionalLaneResolver();
    private final OsmTurnLaneParser turnParser = new OsmTurnLaneParser();
    private final OsmLaneDecomposer decomposer = new OsmLaneDecomposer();
    private final OsmModeAccessResolver accessResolver = new OsmModeAccessResolver();

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

        // Iterate the union of the emitted links and the refs map: a network link missing from refs
        // must still be reported (lane-unresolved-way) rather than silently dropped, and a ref whose
        // link is absent is reported too. Sorted for deterministic issue ordering.
        java.util.Set<String> allLinkIds = new java.util.TreeSet<>(network.getLinks().keySet().stream()
                .map(Object::toString).toList());
        allLinkIds.addAll(refs.keySet());
        for (String linkId : allLinkIds) {
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
            boolean oneway = isOneway(way, rule, config);
            OsmLaneCount count = countResolver.resolve(way.tags(), ref.forward(), oneway,
                    rule.lanesPerDirection());
            // Resolver-level findings (inconsistent-lane-tags, undetermined-lane-split,
            // malformed-lane-count) belong in the bundle report too; otherwise a contradictory tag
            // set silently loses its diagnostic between the resolver and the decomposition.
            for (String code : count.issueCodes()) {
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, code,
                        "Link " + linkId + " lane resolution: " + code, null));
            }
            String turnLanesTag = turnLanes(way, ref.forward(), oneway, count);
            if (turnLanesTag == null && hasBareTurnLanes(way, ref.forward())) {
                // A whole-carriageway turn:lanes on a bidirectional way with an unknown split is
                // ambiguous: it cannot be aligned to a direction, so it is not applied (spec §1b).
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING, "ambiguous-turn-lanes",
                        "Link " + linkId + " has a bare turn:lanes but the way is bidirectional with "
                                + "no even directional split; the tag is ambiguous and not applied", null));
            }
            List<OsmTurnLaneCell> cells = turnParser.parse(turnLanesTag);
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

    /**
     * Direction source shared with network construction (spec §1a): resolve access exactly as
     * {@link OsmSegmentGraph} does and treat a link as one-way unless access grants BOTH forward and
     * backward travel. This makes the lane direction agree with the emitted {@code network.xml},
     * including rule-default one-way ways such as {@code motorway} that carry no literal
     * {@code oneway} tag.
     */
    private boolean isOneway(OsmWayRecord way, OsmWayRule rule, OsmNetworkBuildConfig config) {
        List<OsmModeAccessResolver.DirectionDecision> decisions = accessResolver.resolve(way,
                OsmModeAccessResolver.candidateModes(rule.allowedModes(), config.addBusToCarRoads()),
                rule.defaultOneway());
        boolean forward = false;
        boolean backward = false;
        for (OsmModeAccessResolver.DirectionDecision decision : decisions) {
            if (decision.allowedModes().isEmpty()) {
                continue;
            }
            if (decision.forward()) {
                forward = true;
            }
            if (decision.backward()) {
                backward = true;
            }
        }
        return !(forward && backward);
    }

    /**
     * Directional {@code turn:lanes} always wins. A bare (whole-carriageway) {@code turn:lanes} is
     * only applied when the way is {@code oneway} or the directional count is an even split
     * (spec §1b); otherwise it cannot be orientated to a travel direction and is not applied —
     * {@link #hasBareTurnLanes} lets the caller record the {@code ambiguous-turn-lanes} issue.
     */
    private static String turnLanes(OsmWayRecord way, boolean forward, boolean oneway,
                                    OsmLaneCount count) {
        String directional = way.tags().get("turn:lanes" + (forward ? ":forward" : ":backward"));
        if (directional != null && !directional.isBlank()) {
            return directional;
        }
        if (!oneway && !LaneConfidence.EVEN_SPLIT.equals(count.confidence())) {
            return null;
        }
        return way.tags().get("turn:lanes");
    }

    /** True when a whole-carriageway {@code turn:lanes} exists but no directional tag does. */
    private static boolean hasBareTurnLanes(OsmWayRecord way, boolean forward) {
        String directional = way.tags().get("turn:lanes" + (forward ? ":forward" : ":backward"));
        if (directional != null && !directional.isBlank()) {
            return false;
        }
        String bare = way.tags().get("turn:lanes");
        return bare != null && !bare.isBlank();
    }
}
