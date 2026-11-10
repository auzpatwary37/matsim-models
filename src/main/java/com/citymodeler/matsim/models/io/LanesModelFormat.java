package com.citymodeler.matsim.models.io;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.validation.IssueEntity;
import com.citymodeler.matsim.models.validation.SourceLocation;
import com.citymodeler.matsim.models.validation.ValidationIssue;

/**
 * Reads this library's historic lane format ({@code <lanes>} or a
 * {@code laneDefinitions} envelope containing {@code <assignment@linkId>}
 * elements with comma-separated {@code toLinkIds}/{@code toLaneIds} lane
 * attributes), with the same duplicate/reference/cycle validation as the
 * canonical envelopes.
 */
final class LanesModelFormat {

    private LanesModelFormat() {
    }

    static LaneReadResult parse(Element root, Path source, Network network, String xmlPath) {
        List<ValidationIssue> issues = new ArrayList<>();
        Lanes lanes = new Lanes();
        Set<Id<Link>> seenAssignments = new HashSet<>();
        Map<String, Set<String>> laneIdsByAssignment = new LinkedHashMap<>();
        Map<String, List<String>> toLaneEdges = new LinkedHashMap<>();

        List<Element> assignments = XmlSupport.children(root, "assignment");
        XmlSupport.readAttributes(root, lanes.getAttributes());
        int assignmentIndex = 0;
        for (Element assignmentElement : assignments) {
            assignmentIndex++;
            String assignPath = xmlPath + "/assignment[" + assignmentIndex + "]";
            String linkRef = XmlSupport.attr(assignmentElement, "linkId");
            if (linkRef == null || linkRef.isBlank()) {
                issues.add(LaneEnvelopeReader.error("lanes.missing_link_ref",
                        "assignment without linkId", null,
                        SourceLocation.of(source, assignPath), null, null));
                continue;
            }
            Id<Link> linkId = Id.create(linkRef, Link.class);
            if (!seenAssignments.add(linkId)) {
                issues.add(LaneEnvelopeReader.error("lanes.duplicate_assignment",
                        "Duplicate assignment for link " + linkRef, linkRef,
                        SourceLocation.of(source, assignPath), null, IssueEntity.link(linkRef)));
                continue;
            }
            if (network != null && network.getLinks().get(linkId) == null) {
                issues.add(LaneEnvelopeReader.error("lanes.missing_parent_link",
                        "Lane assignment references unknown link " + linkRef, linkRef,
                        SourceLocation.of(source, assignPath), null, IssueEntity.link(linkRef)));
            }
            LanesToLinkAssignment assignment = new LanesToLinkAssignment(linkId);
            Set<String> laneIds = new LinkedHashSet<>();
            laneIdsByAssignment.put(linkRef, laneIds);
            int laneIndex = 0;
            for (Element laneElement : XmlSupport.children(assignmentElement, "lane")) {
                laneIndex++;
                String lanePath = assignPath + "/lane[" + laneIndex + "]";
                String laneIdText = XmlSupport.attr(laneElement, "id");
                if (laneIdText == null || laneIdText.isBlank()) {
                    issues.add(LaneEnvelopeReader.error("lanes.missing_lane_id",
                            "lane without id", null, SourceLocation.of(source, lanePath),
                            null, null));
                    continue;
                }
                if (!laneIds.add(laneIdText)) {
                    issues.add(LaneEnvelopeReader.error("lanes.duplicate_lane",
                            "Duplicate lane id " + laneIdText + " in assignment " + linkRef,
                            laneIdText, SourceLocation.of(source, lanePath), null,
                            IssueEntity.lane(laneIdText, linkRef)));
                    continue;
                }
                Lane lane = new Lane(Id.create(laneIdText, Lane.class));
                try {
                    addIds(lane, XmlSupport.attr(laneElement, "toLinkIds"), source, lanePath,
                            linkRef, laneIdText, network, issues);
                    addLaneIds(lane, XmlSupport.attr(laneElement, "toLaneIds"));
                    lane.setCapacityVehiclesPerHour(XmlSupport.optionalDouble(
                            laneElement, "capacityVehiclesPerHour", 0.0));
                    lane.setStartsAtMeterFromLinkEnd(XmlSupport.optionalDouble(
                            laneElement, "startsAtMeterFromLinkEnd", 0.0));
                    lane.setAlignment(XmlSupport.attr(laneElement, "alignment"));
                } catch (NumberFormatException exception) {
                    issues.add(LaneEnvelopeReader.error("lanes.malformed_value",
                            "Non-numeric lane value in lane " + laneIdText + " on link "
                                    + linkRef + ": " + exception.getMessage(),
                            laneIdText, SourceLocation.of(source, lanePath), null,
                            IssueEntity.lane(laneIdText, linkRef)));
                    continue;
                }
                XmlSupport.readAttributes(laneElement, lane.getAttributes());
                List<String> edges = new ArrayList<>();
                for (Id<Lane> toLane : lane.getToLaneIds()) {
                    edges.add(toLane.toString());
                }
                toLaneEdges.put(ScopedLaneId.of(linkRef, laneIdText), edges);
                assignment.addLane(lane);
            }
            lanes.addAssignment(assignment);
        }

        issues.addAll(LaneEnvelopeReader.cycleIssues(toLaneEdges, laneIdsByAssignment,
                source, xmlPath));
        SourceLocation location = SourceLocation.of(source, xmlPath);
        boolean hasErrors = issues.stream().anyMatch(ValidationIssue::isError);
        if (hasErrors) {
            return LaneReadResult.invalid(LaneDialect.MODELS_1_0, location, issues);
        }
        return LaneReadResult.present(lanes, LaneDialect.MODELS_1_0, location, issues);
    }

    private static void addIds(Lane lane, String value, Path source, String lanePath,
            String linkRef, String laneIdText, Network network, List<ValidationIssue> issues) {
        for (String id : splitTokens(value)) {
            Id<Link> linkId = Id.create(id, Link.class);
            if (network != null && network.getLinks().get(linkId) == null) {
                issues.add(LaneEnvelopeReader.error("lanes.unresolved_link_ref",
                        "Lane " + laneIdText + " on link " + linkRef
                                + " references unknown link " + id,
                        laneIdText, SourceLocation.of(source, lanePath),
                        IssueEntity.lane(laneIdText, linkRef), IssueEntity.link(id)));
            }
            lane.addToLinkId(linkId);
        }
    }

    private static void addLaneIds(Lane lane, String value) {
        for (String id : splitTokens(value)) {
            lane.addToLaneId(Id.create(id, Lane.class));
        }
    }

    private static List<String> splitTokens(String value) {
        List<String> tokens = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return tokens;
        }
        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }
}
