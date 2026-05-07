package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;

public final class LanesXmlWriter {
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
        Element root = document.createElement("lanes");
        document.appendChild(root);
        for (LanesToLinkAssignment assignment : lanes.getLanesToLinkAssignments().values()) {
            Element assignmentElement = document.createElement("assignment");
            assignmentElement.setAttribute("linkId", assignment.getLinkId().toString());
            root.appendChild(assignmentElement);
            for (Lane lane : assignment.getLanes().values()) {
                Element laneElement = document.createElement("lane");
                laneElement.setAttribute("id", lane.getId().toString());
                XmlSupport.setIfPresent(laneElement, "toLinkIds", join(lane.getToLinkIds()));
                XmlSupport.setIfPresent(laneElement, "toLaneIds", join(lane.getToLaneIds()));
                laneElement.setAttribute("capacityVehiclesPerHour", Double.toString(lane.getCapacityVehiclesPerHour()));
                laneElement.setAttribute("startsAtMeterFromLinkEnd", Double.toString(lane.getStartsAtMeterFromLinkEnd()));
                XmlSupport.setIfPresent(laneElement, "alignment", lane.getAlignment());
                XmlSupport.appendAttributes(document, laneElement, lane.getAttributes());
                assignmentElement.appendChild(laneElement);
            }
        }
        return document;
    }

    private static String join(List<? extends Id<?>> ids) {
        if (ids.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        ids.forEach(id -> joiner.add(id.toString()));
        return joiner.toString();
    }
}
