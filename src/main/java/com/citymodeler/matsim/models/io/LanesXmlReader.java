package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;

/**
 * Reads the published MATSim {@code laneDefinitions_v2.0} shape. Feeding a
 * legacy {@code <lanes>/<assignment>} document yields an empty model (the
 * legacy element names are not recognized); that tolerance is intentional and
 * requires no special handling.
 */
public final class LanesXmlReader {
    private static final String SCHEMA = "/schemas/v2/laneDefinitions_v2.0.xsd";
    private final boolean validateSchema;

    public LanesXmlReader() {
        this(false);
    }

    public LanesXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public Lanes read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public Lanes read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public Lanes read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    private Lanes read(Element root) {
        Lanes lanes = new Lanes();
        XmlSupport.readAttributes(root, lanes.getAttributes());
        for (Element assignmentElement : XmlSupport.children(root, "lanesToLinkAssignment")) {
            LanesToLinkAssignment assignment = new LanesToLinkAssignment(
                    Id.create(XmlSupport.attr(assignmentElement, "linkIdRef"), Link.class));
            for (Element laneElement : XmlSupport.children(assignmentElement, "lane")) {
                Lane lane = new Lane(Id.create(XmlSupport.attr(laneElement, "id"), Lane.class));
                Element leadsTo = XmlSupport.child(laneElement, "leadsTo");
                if (leadsTo != null) {
                    for (Element toLink : XmlSupport.children(leadsTo, "toLink")) {
                        lane.addToLinkId(Id.create(XmlSupport.attr(toLink, "refId"), Link.class));
                    }
                    for (Element toLane : XmlSupport.children(leadsTo, "toLane")) {
                        lane.addToLaneId(Id.create(XmlSupport.attr(toLane, "refId"), Lane.class));
                    }
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
                XmlSupport.readAttributes(laneElement, lane.getAttributes());
                rehydrateToLaneIds(lane);
                assignment.addLane(lane);
            }
            lanes.addAssignment(assignment);
        }
        return lanes;
    }

    /**
     * Restores the {@code toLane} ids a mixed lane had to drop from {@code <leadsTo>} (the published
     * {@code xs:choice} cannot carry both branches). The writer joins them into the
     * {@code osm:lane.toLaneIds} attribute; parsing it back makes a mixed-lane relationship
     * reversible through a write/read round-trip rather than degrading to metadata (spec Part 2).
     * Ids already present from an explicit {@code toLane} branch are not duplicated.
     */
    private static void rehydrateToLaneIds(Lane lane) {
        Object raw = lane.getAttributes().getAttribute(LanesXmlWriter.TO_LANE_IDS_ATTRIBUTE);
        if (raw == null) {
            return;
        }
        java.util.Set<String> existing = new java.util.LinkedHashSet<>();
        for (Id<Lane> id : lane.getToLaneIds()) {
            existing.add(id.toString());
        }
        for (String token : raw.toString().split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty() || !existing.add(trimmed)) {
                continue;
            }
            lane.addToLaneId(Id.create(trimmed, Lane.class));
        }
    }
}
