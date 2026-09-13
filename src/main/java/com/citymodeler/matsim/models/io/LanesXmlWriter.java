package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;

/**
 * Writes lane definitions in the published MATSim {@code laneDefinitions_v2.0}
 * wire format: a {@code laneDefinitions} root in the
 * {@code http://www.matsim.org/files/dtd} namespace declaring the
 * {@code laneDefinitions_v2.0.xsd} schema location, containing
 * {@code <lanesToLinkAssignment>} elements whose {@code <lane>} children follow
 * the fixed XSD order {@code leadsTo, representedLanes, capacity, startsAt,
 * alignment, attributes}.
 *
 * <p>Schema-shape normalization applied on write:</p>
 * <ul>
 *   <li>{@code leadsTo} is an {@code xs:choice}: the {@code toLink} branch is
 *       emitted when the lane has any {@code toLink} ids, otherwise the
 *       {@code toLane} branch. When both are present the dropped {@code toLane}
 *       ids are preserved in the lane attribute {@code osm:lane.toLaneIds}.</li>
 *   <li>{@code alignment} is an {@code xs:int}: a valid integer is emitted
 *       as-is; null, blank, or non-integer input emits {@code 0} plus the lane
 *       attribute {@code osm:lane.alignmentProvenance=default} (and
 *       {@code osm:lane.alignmentRaw=<original>} for a non-blank non-numeric
 *       value).</li>
 *   <li>A lane with neither {@code toLink} nor {@code toLane} ids is rejected
 *       with {@link IllegalArgumentException} (the XSD requires a non-empty
 *       choice).</li>
 *   <li>{@code capacity}, {@code startsAt} and {@code representedLanes} are
 *       omitted when absent, relying on the schema defaults.</li>
 * </ul>
 *
 * <p>Read/write asymmetry: the published {@code laneDefinitions} root carries no
 * attributes, so this writer never emits root attributes, whereas
 * {@link LanesXmlReader} keeps a lenient read of any legacy root attributes for
 * tolerance only. Writer-generated provenance attributes are written to a local
 * copy of the lane's attributes; the caller's {@link Lane} is never mutated.</p>
 *
 * <p>The element names and structure are defined by the published v2.0
 * schema; no MATSim or pt2MATSim code is used.</p>
 */
public final class LanesXmlWriter {
    static final String MATSIM_NAMESPACE = "http://www.matsim.org/files/dtd";
    static final String SCHEMA_LOCATION =
            MATSIM_NAMESPACE + " " + MATSIM_NAMESPACE + "/laneDefinitions_v2.0.xsd";
    static final String ALIGNMENT_PROVENANCE_ATTRIBUTE = "osm:lane.alignmentProvenance";
    static final String ALIGNMENT_RAW_ATTRIBUTE = "osm:lane.alignmentRaw";
    static final String TO_LANE_IDS_ATTRIBUTE = "osm:lane.toLaneIds";

    public void write(Lanes lanes, Path path) {
        XmlSupport.write(document(lanes), path);
    }

    public void write(Lanes lanes, OutputStream outputStream) {
        XmlSupport.write(document(lanes), outputStream);
    }

    public String writeToString(Lanes lanes) {
        return XmlSupport.writeToString(document(lanes));
    }

    private Document document(Lanes lanes) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElementNS(MATSIM_NAMESPACE, "laneDefinitions");
        root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation",
                SCHEMA_LOCATION);
        document.appendChild(root);
        for (LanesToLinkAssignment assignment : lanes.getLanesToLinkAssignments().values()) {
            Element assignmentElement = document.createElementNS(MATSIM_NAMESPACE, "lanesToLinkAssignment");
            assignmentElement.setAttribute("linkIdRef", assignment.getLinkId().toString());
            root.appendChild(assignmentElement);
            for (Lane lane : assignment.getLanes().values()) {
                Element laneElement = document.createElementNS(MATSIM_NAMESPACE, "lane");
                laneElement.setAttribute("id", lane.getId().toString());

                Attributes emittedAttributes = new Attributes();
                for (var entry : lane.getAttributes().getAsMap().entrySet()) {
                    emittedAttributes.putAttribute(entry.getKey(), entry.getValue());
                }

                laneElement.appendChild(leadsToElement(document, lane, emittedAttributes));

                if (lane.getCapacityVehiclesPerHour() > 0.0) {
                    Element capacity = document.createElementNS(MATSIM_NAMESPACE, "capacity");
                    capacity.setAttribute("vehiclesPerHour",
                            Double.toString(lane.getCapacityVehiclesPerHour()));
                    laneElement.appendChild(capacity);
                }
                if (lane.getStartsAtMeterFromLinkEnd() > 0.0) {
                    Element startsAt = document.createElementNS(MATSIM_NAMESPACE, "startsAt");
                    startsAt.setAttribute("meterFromLinkEnd",
                            Double.toString(lane.getStartsAtMeterFromLinkEnd()));
                    laneElement.appendChild(startsAt);
                }

                Element alignment = document.createElementNS(MATSIM_NAMESPACE, "alignment");
                alignment.setTextContent(normalizeAlignment(lane.getAlignment(), emittedAttributes));
                laneElement.appendChild(alignment);

                XmlSupport.appendAttributes(document, laneElement, emittedAttributes, MATSIM_NAMESPACE);
                assignmentElement.appendChild(laneElement);
            }
        }
        return document;
    }

    private static Element leadsToElement(Document document, Lane lane, Attributes emittedAttributes) {
        Element leadsTo = document.createElementNS(MATSIM_NAMESPACE, "leadsTo");
        List<Id<Link>> toLinkIds = lane.getToLinkIds();
        List<Id<Lane>> toLaneIds = lane.getToLaneIds();
        if (!toLinkIds.isEmpty()) {
            for (Id<Link> toLink : toLinkIds) {
                Element toLinkElement = document.createElementNS(MATSIM_NAMESPACE, "toLink");
                toLinkElement.setAttribute("refId", toLink.toString());
                leadsTo.appendChild(toLinkElement);
            }
            if (!toLaneIds.isEmpty()) {
                emittedAttributes.putAttribute(TO_LANE_IDS_ATTRIBUTE, joinIds(toLaneIds));
            }
            return leadsTo;
        }
        if (!toLaneIds.isEmpty()) {
            for (Id<Lane> toLane : toLaneIds) {
                Element toLaneElement = document.createElementNS(MATSIM_NAMESPACE, "toLane");
                toLaneElement.setAttribute("refId", toLane.toString());
                leadsTo.appendChild(toLaneElement);
            }
            return leadsTo;
        }
        throw new IllegalArgumentException("Lane " + lane.getId()
                + " has neither toLink nor toLane ids; the published schema requires a non-empty leadsTo");
    }

    private static String normalizeAlignment(String alignment, Attributes emittedAttributes) {
        if (alignment != null) {
            String trimmed = alignment.trim();
            if (!trimmed.isEmpty()) {
                try {
                    return Integer.toString(Integer.parseInt(trimmed));
                } catch (NumberFormatException exception) {
                    emittedAttributes.putAttribute(ALIGNMENT_RAW_ATTRIBUTE, alignment);
                }
            }
        }
        emittedAttributes.putAttribute(ALIGNMENT_PROVENANCE_ATTRIBUTE, "default");
        return "0";
    }

    private static String joinIds(List<? extends Id<?>> ids) {
        StringJoiner joiner = new StringJoiner(",");
        for (Id<?> id : ids) {
            joiner.add(id.toString());
        }
        return joiner.toString();
    }
}
