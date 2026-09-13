package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;

/**
 * Turns a resolved directional lane count plus {@code turn:lanes} cells into the lane model.
 * Never fabricates a movement: when evidence is missing or unsupported a lane receives every
 * geometrically-available outgoing link (the schema-mandatory "unrestricted" fallback) and the
 * confidence attribute records why.
 *
 * <p>Precondition: {@code outgoingLinkIds} must be non-null and contain at least one non-null
 * element. A lane cannot exist without a successor link, so a dead-end link (no outgoing links)
 * must be handled by the caller — this decomposer fails fast rather than emitting a lane with an
 * empty schema-mandatory {@code leadsTo} list.
 */
public final class OsmLaneDecomposer {

    public LaneDecomposition decompose(String linkId, OsmLaneCount count, List<OsmTurnLaneCell> cells,
                                       List<String> outgoingLinkIds, MovementTurnClassifier classifier,
                                       double capacityPerLane) {
        if (outgoingLinkIds == null || outgoingLinkIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "outgoingLinkIds must not be null or empty: a lane requires at least one successor link");
        }
        for (String outgoingLinkId : outgoingLinkIds) {
            if (outgoingLinkId == null) {
                throw new IllegalArgumentException("outgoingLinkIds must not contain null elements");
            }
        }

        List<OsmImportIssue> issues = new ArrayList<>();
        int laneCount = count.lanes();
        List<OsmTurnLaneCell> aligned = cells;

        if (!cells.isEmpty() && cells.size() != laneCount) {
            issues.add(issue("lane-count-mismatch",
                    "Link " + linkId + " has " + laneCount + " lanes but " + cells.size()
                            + " turn:lanes cells"));
            if (LaneConfidence.UNDETERMINED_SPLIT.equals(count.confidence())
                    || LaneConfidence.ABSENT.equals(count.confidence())) {
                laneCount = cells.size();
            }
        }

        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create(linkId, Link.class));
        for (int i = 0; i < laneCount; i++) {
            OsmTurnLaneCell cell = i < aligned.size() ? aligned.get(i) : null;
            assignment.addLane(buildLane(linkId, i, cell, count, cells.size(), outgoingLinkIds,
                    classifier, capacityPerLane, issues));
        }
        return new LaneDecomposition(assignment, issues);
    }

    private Lane buildLane(String linkId, int index, OsmTurnLaneCell cell, OsmLaneCount count,
                           int turnLanesCount, List<String> outgoingLinkIds,
                           MovementTurnClassifier classifier, double capacityPerLane,
                           List<OsmImportIssue> issues) {
        Lane lane = new Lane(Id.create(linkId + "_l" + index, Lane.class));
        lane.setCapacityVehiclesPerHour(capacityPerLane);
        lane.getAttributes().putAttribute("osm:lane.index", index);
        lane.getAttributes().putAttribute("osm:lanes.count", count.lanes());
        if (turnLanesCount > 0) {
            lane.getAttributes().putAttribute("osm:turnLanes.count", turnLanesCount);
        }
        if (count.undeterminedTotal() != null) {
            lane.getAttributes().putAttribute("osm:lanes.total", count.undeterminedTotal());
        }

        Set<String> toLinks = new LinkedHashSet<>();
        String confidence = count.confidence();

        if (cell == null || cell.empty()) {
            confidence = LaneConfidence.ABSENT;
            if (cell != null && cell.empty()) {
                issues.add(issue("empty-turn-cell", "Link " + linkId + " lane " + index + " is empty"));
            }
            toLinks.addAll(outgoingLinkIds);
        } else if (!cell.unsupportedTokens().isEmpty()) {
            confidence = LaneConfidence.UNSUPPORTED;
            issues.add(issue("unsupported-turn-token", "Link " + linkId + " lane " + index
                    + " has unsupported token(s) " + cell.unsupportedTokens()));
            toLinks.addAll(outgoingLinkIds);
        } else if (cell.none()) {
            // "none" is explicit evidence that the lane carries no marked indication, distinct from
            // "no evidence" (absent).
            confidence = LaneConfidence.NONE_OBSERVED;
            toLinks.addAll(outgoingLinkIds);
        } else if (!cell.indications().isEmpty()) {
            if (LaneConfidence.UNDETERMINED_SPLIT.equals(count.confidence())
                    || LaneConfidence.ABSENT.equals(count.confidence())) {
                confidence = LaneConfidence.TURN_LANES_AUTHORITATIVE;
            }
            for (LaneTurnClass indication : cell.indications()) {
                toLinks.addAll(resolve(linkId, indication, outgoingLinkIds, classifier));
            }
            if (toLinks.isEmpty()) {
                // Indications were present and parsed but resolve to no movement: keep that as
                // "partial" (evidence present but unresolved), never overwritten to "absent".
                confidence = LaneConfidence.PARTIAL;
            }
        }

        if (cell != null && cell.merge() != LaneMerge.NONE) {
            lane.getAttributes().putAttribute("osm:lane.merge",
                    cell.merge() == LaneMerge.LEFT ? "left" : "right");
            toLinks.addAll(outgoingLinkIds);
            confidence = LaneConfidence.MERGE;
        }
        if (toLinks.isEmpty()) {
            toLinks.addAll(outgoingLinkIds);
            if (!LaneConfidence.PARTIAL.equals(confidence)) {
                confidence = LaneConfidence.ABSENT;
            }
        }
        for (String id : toLinks) {
            lane.addToLinkId(Id.create(id, Link.class));
        }
        lane.getAttributes().putAttribute("osm:lane.confidence", confidence);
        if (cell != null) {
            lane.getAttributes().putAttribute("osm:lane.rawToken", cell.raw());
        }
        lane.getAttributes().putAttribute("osm:lane.capacity.shared",
                Boolean.toString(toLinks.size() > 1));
        return lane;
    }

    private static List<String> resolve(String inLinkId, LaneTurnClass indication, List<String> outgoing,
                                        MovementTurnClassifier classifier) {
        Map<LaneTurnClass, List<String>> byClass = new LinkedHashMap<>();
        for (String out : outgoing) {
            byClass.computeIfAbsent(classifier.classify(inLinkId, out), k -> new ArrayList<>()).add(out);
        }
        List<String> direct = byClass.getOrDefault(indication, List.of());
        if (!direct.isEmpty()) {
            return direct;
        }
        // Documented, flagged fallback (spec §1b): a sharp turn with no sharp movement falls back to
        // the base left/right movement. No base→slight fallback is applied.
        LaneTurnClass fallback = switch (indication) {
            case SHARP_LEFT -> LaneTurnClass.LEFT;
            case SHARP_RIGHT -> LaneTurnClass.RIGHT;
            default -> LaneTurnClass.UNKNOWN;
        };
        return byClass.getOrDefault(fallback, List.of());
    }

    private static OsmImportIssue issue(String code, String message) {
        return new OsmImportIssue(OsmIssueSeverity.WARNING, code, message, null);
    }
}
