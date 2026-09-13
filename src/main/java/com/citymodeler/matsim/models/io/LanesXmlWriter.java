package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
 * <p>The element names and structure are defined by the published v2.0
 * schema; no MATSim or pt2MATSim code is used.</p>
 */
public final class LanesXmlWriter {
    static final String MATSIM_NAMESPACE = "http://www.matsim.org/files/dtd";
    static final String SCHEMA_LOCATION =
            MATSIM_NAMESPACE + " " + MATSIM_NAMESPACE + "/laneDefinitions_v2.0.xsd";

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

                Element leadsTo = document.createElementNS(MATSIM_NAMESPACE, "leadsTo");
                for (Id<Link> toLink : lane.getToLinkIds()) {
                    Element toLinkElement = document.createElementNS(MATSIM_NAMESPACE, "toLink");
                    toLinkElement.setAttribute("refId", toLink.toString());
                    leadsTo.appendChild(toLinkElement);
                }
                for (Id<Lane> toLane : lane.getToLaneIds()) {
                    Element toLaneElement = document.createElementNS(MATSIM_NAMESPACE, "toLane");
                    toLaneElement.setAttribute("refId", toLane.toString());
                    leadsTo.appendChild(toLaneElement);
                }
                laneElement.appendChild(leadsTo);

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
                alignment.setTextContent(lane.getAlignment() != null ? lane.getAlignment() : "0");
                laneElement.appendChild(alignment);

                XmlSupport.appendAttributes(document, laneElement, lane.getAttributes(), MATSIM_NAMESPACE);
                assignmentElement.appendChild(laneElement);
            }
        }
        return document;
    }
}
