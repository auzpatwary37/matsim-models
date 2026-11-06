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
import com.citymodeler.matsim.models.validation.ValidationSeverity;

/**
 * Shared lane-envelope reader used by {@link NetworkDocumentReader} (embedded
 * blocks) and {@link LanesXmlReader#readResult}. Recognizes the canonical
 * {@code laneDefinitions} envelopes (v2.0 with {@code leadsTo} choice, v1.1
 * with direct {@code toLink} children) and this library's historic
 * {@code lanes}/{@code assignment} format by STRUCTURE, confirmed against
 * version/schema-location hints; conflicting hints or mixed structures are
 * INVALID, and unrecognized lane content is UNSUPPORTED — never a silent
 * empty model.
 *
 * <p>Reference validation runs against a caller-supplied {@link Network}
 * (nullable for schema-only reads): missing parent links, unresolved link
 * refs, duplicates and toLane cycles become ERROR issues and invalidate the
 * result.</p>
 */
final class LaneEnvelopeReader {

    private LaneEnvelopeReader() {
    }

    /** Parses a lane document root (explicit file) with reference validation. */
    static LaneReadResult readDocument(Element root, Path source, Network network) {
        String name = XmlSupport.localName(root);
        String xmlPath = "/" + name + "[1]";
        if ("laneDefinitions".equals(name)) {
            return readEnvelope(root, source, network, xmlPath);
        }
        if ("lanes".equals(name)) {
            return LanesModelFormat.parse(root, source, network, xmlPath);
        }
        SourceLocation location = SourceLocation.of(source, xmlPath);
        return LaneReadResult.unsupported(location, List.of(error("lanes.unsupported_root",
                "Root element '" + name + "' is not a recognized lane definitions document",
                null, location, null, null)));
    }

    /**
     * Parses a {@code <laneDefinitions>} envelope (embedded or file root).
     *
     * @param xmlPath structural path of the envelope with occurrence indices
     */
    static LaneReadResult readEnvelope(Element envelope, Path source, Network network,
            String xmlPath) {
        List<ValidationIssue> issues = new ArrayList<>();
        LaneDialect hinted = hintOf(envelope);

        List<Element> assignments = XmlSupport.children(envelope, "lanesToLinkAssignment");
        boolean v2Shapes = false;
        boolean v11Shapes = false;
        for (Element assignmentElement : assignments) {
            for (Element laneElement : XmlSupport.children(assignmentElement, "lane")) {
                if (XmlSupport.child(laneElement, "leadsTo") != null) {
                    v2Shapes = true;
                }
                if (!XmlSupport.children(laneElement, "toLink").isEmpty()) {
                    v11Shapes = true;
                }
                if (XmlSupport.attr(laneElement, "toLinkIds") != null
                        || XmlSupport.attr(laneElement, "toLaneIds") != null) {
                    issues.add(error("lanes.mixed_dialect",
                            "lane uses attribute-style references inside a canonical "
                                    + "laneDefinitions envelope",
                            XmlSupport.attr(laneElement, "id"),
                            SourceLocation.of(source, xmlPath), null, null));
                    return LaneReadResult.invalid(hinted,
                            SourceLocation.of(source, xmlPath), issues);
                }
            }
        }
        if (v2Shapes && v11Shapes) {
            issues.add(error("lanes.mixed_dialect",
                    "Envelope mixes v2.0 leadsTo and v1.1 direct toLink structures",
                    null, SourceLocation.of(source, xmlPath), null, null));
            return LaneReadResult.invalid(hinted, SourceLocation.of(source, xmlPath), issues);
        }
        boolean modelsShapes = !XmlSupport.children(envelope, "assignment").isEmpty();
        if (modelsShapes && (!assignments.isEmpty() || hinted != null)) {
            issues.add(error("lanes.mixed_dialect",
                    "Envelope mixes assignment-style children with canonical structures",
                    null, SourceLocation.of(source, xmlPath), null, null));
            return LaneReadResult.invalid(hinted, SourceLocation.of(source, xmlPath), issues);
        }
        if (modelsShapes) {
            return LanesModelFormat.parse(envelope, source, network, xmlPath);
        }

        LaneDialect detected;
        if (!assignments.isEmpty()) {
            detected = v11Shapes ? LaneDialect.MATSIM_V1_1 : LaneDialect.MATSIM_V2_0;
            if (hinted != null && hinted != detected) {
                issues.add(error("lanes.mixed_dialect",
                        "Structure indicates " + detected + " but the envelope hint says " + hinted,
                        null, SourceLocation.of(source, xmlPath), null, null));
                return LaneReadResult.invalid(hinted, SourceLocation.of(source, xmlPath), issues);
            }
        } else if (hinted != null) {
            return LaneReadResult.present(new Lanes(), hinted,
                    SourceLocation.of(source, xmlPath), List.of());
        } else {
            SourceLocation location = SourceLocation.of(source, xmlPath);
            return LaneReadResult.unsupported(location, List.of(error("lanes.ambiguous_dialect",
                    "Empty laneDefinitions envelope without a version or schema-location hint; "
                            + "dialect cannot be identified without guessing",
                    null, location, null, null)));
        }

        Lanes lanes = new Lanes();
        Set<Id<Link>> seenAssignments = new HashSet<>();
        Map<String, Set<String>> laneIdsByAssignment = new LinkedHashMap<>();
        Map<String, List<String>> toLaneEdges = new LinkedHashMap<>();
        List<ValidationIssue> local = new ArrayList<>();

        int assignmentIndex = 0;
        for (Element assignmentElement : assignments) {
            assignmentIndex++;
            String assignPath = xmlPath + "/lanesToLinkAssignment[" + assignmentIndex + "]";
            String linkRef = XmlSupport.attr(assignmentElement, "linkIdRef");
            if (linkRef == null || linkRef.isBlank()) {
                local.add(error("lanes.missing_link_ref",
                        "lanesToLinkAssignment without linkIdRef", null,
                        SourceLocation.of(source, assignPath), null, null));
                continue;
            }
            Id<Link> linkId = Id.create(linkRef, Link.class);
            if (!seenAssignments.add(linkId)) {
                local.add(error("lanes.duplicate_assignment",
                        "Duplicate lanesToLinkAssignment for link " + linkRef, linkRef,
                        SourceLocation.of(source, assignPath), null, IssueEntity.link(linkRef)));
                continue;
            }
            if (network != null && network.getLinks().get(linkId) == null) {
                local.add(error("lanes.missing_parent_link",
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
                    local.add(error("lanes.missing_lane_id", "lane without id", null,
                            SourceLocation.of(source, lanePath), null, null));
                    continue;
                }
                if (!laneIds.add(laneIdText)) {
                    local.add(error("lanes.duplicate_lane",
                            "Duplicate lane id " + laneIdText + " in assignment " + linkRef,
                            laneIdText, SourceLocation.of(source, lanePath), null,
                            IssueEntity.lane(laneIdText, linkRef)));
                    continue;
                }
                Lane lane;
                try {
                    lane = parseLane(laneElement, linkRef, laneIdText, source, lanePath, local);
                } catch (NumberFormatException exception) {
                    local.add(error("lanes.malformed_value",
                            "Non-numeric lane value in lane " + laneIdText + " on link " + linkRef
                                    + ": " + exception.getMessage(),
                            laneIdText, SourceLocation.of(source, lanePath), null,
                            IssueEntity.lane(laneIdText, linkRef)));
                    continue;
                }
                for (Id<Link> toLink : lane.getToLinkIds()) {
                    if (network != null && network.getLinks().get(toLink) == null) {
                        local.add(error("lanes.unresolved_link_ref",
                                "Lane " + laneIdText + " on link " + linkRef
                                        + " references unknown link " + toLink,
                                laneIdText, SourceLocation.of(source, lanePath),
                                IssueEntity.lane(laneIdText, linkRef),
                                IssueEntity.link(toLink.toString())));
                    }
                }
                List<String> edges = new ArrayList<>();
                for (Id<Lane> toLane : lane.getToLaneIds()) {
                    edges.add(toLane.toString());
                }
                toLaneEdges.put(ScopedLaneId.of(linkRef, laneIdText), edges);
                assignment.addLane(lane);
            }
            lanes.addAssignment(assignment);
        }

        local.addAll(cycleIssues(toLaneEdges, laneIdsByAssignment, source, xmlPath));
        issues.addAll(local);
        boolean hasErrors = issues.stream().anyMatch(ValidationIssue::isError);
        if (hasErrors) {
            return LaneReadResult.invalid(detected, SourceLocation.of(source, xmlPath), issues);
        }
        return LaneReadResult.present(lanes, detected,
                SourceLocation.of(source, xmlPath), issues);
    }

    private static Lane parseLane(Element laneElement, String linkRef, String laneIdText,
            Path source, String lanePath, List<ValidationIssue> issues) {
        Lane lane = new Lane(Id.create(laneIdText, Lane.class));
        Element leadsTo = XmlSupport.child(laneElement, "leadsTo");
        if (leadsTo != null) {
            for (Element toLink : XmlSupport.children(leadsTo, "toLink")) {
                lane.addToLinkId(Id.create(XmlSupport.attr(toLink, "refId"), Link.class));
            }
            for (Element toLane : XmlSupport.children(leadsTo, "toLane")) {
                lane.addToLaneId(Id.create(XmlSupport.attr(toLane, "refId"), Lane.class));
            }
        }
        for (Element toLink : XmlSupport.children(laneElement, "toLink")) {
            lane.addToLinkId(Id.create(XmlSupport.attr(toLink, "refId"), Link.class));
        }
        if (lane.getToLinkIds().isEmpty() && lane.getToLaneIds().isEmpty()) {
            issues.add(error("lanes.no_leads_to",
                    "Lane " + laneIdText + " on link " + linkRef
                            + " has neither leadsTo nor toLink targets",
                    laneIdText, SourceLocation.of(source, lanePath), null,
                    IssueEntity.lane(laneIdText, linkRef)));
        }
        Element capacity = XmlSupport.child(laneElement, "capacity");
        if (capacity != null) {
            lane.setCapacityVehiclesPerHour(
                    XmlSupport.optionalDouble(capacity, "vehiclesPerHour", 0.0));
        }
        Element startsAt = XmlSupport.child(laneElement, "startsAt");
        if (startsAt != null) {
            lane.setStartsAtMeterFromLinkEnd(
                    XmlSupport.optionalDouble(startsAt, "meterFromLinkEnd", 0.0));
        }
        Element alignment = XmlSupport.child(laneElement, "alignment");
        if (alignment != null) {
            lane.setAlignment(alignment.getTextContent());
        }
        Element representedLanes = XmlSupport.child(laneElement, "representedLanes");
        if (representedLanes != null) {
            String number = XmlSupport.attr(representedLanes, "number");
            if (number != null && !number.isBlank()) {
                double parsed = Double.parseDouble(number);
                if (parsed <= 0.0) {
                    issues.add(error("lanes.malformed_value",
                            "representedLanes number must be > 0 (lane " + laneIdText + ")",
                            laneIdText, SourceLocation.of(source, lanePath), null,
                            IssueEntity.lane(laneIdText, linkRef)));
                } else {
                    lane.setRepresentedLanesNumber(parsed);
                }
            }
        }
        Element length = XmlSupport.child(laneElement, "length");
        if (length != null) {
            String meter = XmlSupport.attr(length, "meter");
            if (meter != null && !meter.isBlank()) {
                lane.setLengthMeter(Double.parseDouble(meter));
            }
        }
        XmlSupport.readAttributes(laneElement, lane.getAttributes());
        LanesXmlReader.rehydrateToLaneIds(lane);
        if (lane.getLengthMeter() == null) {
            Object rawLength =
                    lane.getAttributes().getAttribute(LanesXmlWriter.LENGTH_METER_ATTRIBUTE);
            if (rawLength != null && !rawLength.toString().isBlank()) {
                lane.setLengthMeter(Double.parseDouble(rawLength.toString().trim()));
            }
        }
        return lane;
    }

    private static LaneDialect hintOf(Element envelope) {
        for (String attribute : new String[] { "xsi:noNamespaceSchemaLocation",
                "xsi:schemaLocation", "schemaVersion" }) {
            String value = XmlSupport.attr(envelope, attribute);
            if (value == null) {
                continue;
            }
            if (value.contains("laneDefinitions_v2")) {
                return LaneDialect.MATSIM_V2_0;
            }
            if (value.contains("laneDefinitions_v1")) {
                return LaneDialect.MATSIM_V1_1;
            }
        }
        return null;
    }

    /** Detects toLane cycles (scoped walk; cycle-breaking lanes reported). */
    static List<ValidationIssue> cycleIssues(Map<String, List<String>> toLaneEdges,
            Map<String, Set<String>> laneIdsByAssignment, Path source, String xmlPath) {
        Map<String, List<String>> scopedAdjacency = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : toLaneEdges.entrySet()) {
            String fromLink = ScopedLaneId.parse(entry.getKey()).linkId();
            List<String> resolved = new ArrayList<>();
            for (String target : entry.getValue()) {
                resolved.add(ScopedLaneId.of(ownerOf(target, fromLink, laneIdsByAssignment),
                        target));
            }
            scopedAdjacency.put(entry.getKey(), resolved);
        }
        List<ValidationIssue> issues = new ArrayList<>();
        Set<String> reported = new HashSet<>();
        for (String start : scopedAdjacency.keySet()) {
            Set<String> path = new LinkedHashSet<>();
            String node = start;
            while (node != null && path.add(node)) {
                List<String> nexts = scopedAdjacency.getOrDefault(node, List.of());
                node = nexts.isEmpty() ? null : nexts.get(0);
                if (node != null && path.contains(node) && reported.add(node)) {
                    ScopedLaneId lane = ScopedLaneId.parse(node);
                    issues.add(error("lanes.cycle",
                            "toLane reference cycle reaches lane " + lane.laneId()
                                    + " on link " + lane.linkId(),
                            lane.laneId(), SourceLocation.of(source, xmlPath), null,
                            IssueEntity.lane(lane.laneId(), lane.linkId())));
                    break;
                }
            }
        }
        return issues;
    }

    /** Resolves a bare lane id to its owning assignment link (own link wins). */
    static String ownerOf(String laneId, String ownLink, Map<String, Set<String>> lanesByAssignment) {
        if (lanesByAssignment.getOrDefault(ownLink, Set.of()).contains(laneId)) {
            return ownLink;
        }
        String owner = null;
        for (Map.Entry<String, Set<String>> entry : lanesByAssignment.entrySet()) {
            if (entry.getValue().contains(laneId)) {
                if (owner != null) {
                    return ownLink;
                }
                owner = entry.getKey();
            }
        }
        return owner == null ? ownLink : owner;
    }

    static ValidationIssue error(String code, String message, String objectId,
            SourceLocation location, IssueEntity owner, IssueEntity referenced) {
        return new ValidationIssue(ValidationSeverity.ERROR, "lanes", code, message,
                objectId, null, location, owner, referenced);
    }
}
