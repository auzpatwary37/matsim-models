package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;

public final class LanesXmlReader {
    public Lanes read(Path path) {
        return read(XmlSupport.parse(path).getDocumentElement());
    }

    public Lanes read(InputStream inputStream) {
        return read(XmlSupport.parse(inputStream).getDocumentElement());
    }

    public Lanes read(String xml) {
        return read(XmlSupport.parse(xml).getDocumentElement());
    }

    private Lanes read(Element root) {
        Lanes lanes = new Lanes();
        XmlSupport.readAttributes(root, lanes.getAttributes());
        for (Element assignmentElement : XmlSupport.children(root, "assignment")) {
            LanesToLinkAssignment assignment = new LanesToLinkAssignment(
                    Id.create(XmlSupport.attr(assignmentElement, "linkId"), Link.class));
            for (Element laneElement : XmlSupport.children(assignmentElement, "lane")) {
                Lane lane = new Lane(Id.create(XmlSupport.attr(laneElement, "id"), Lane.class));
                addLinkIds(lane, XmlSupport.attr(laneElement, "toLinkIds"));
                addLaneIds(lane, XmlSupport.attr(laneElement, "toLaneIds"));
                lane.setCapacityVehiclesPerHour(XmlSupport.optionalDouble(laneElement, "capacityVehiclesPerHour", 0.0));
                lane.setStartsAtMeterFromLinkEnd(XmlSupport.optionalDouble(laneElement, "startsAtMeterFromLinkEnd", 0.0));
                lane.setAlignment(XmlSupport.attr(laneElement, "alignment"));
                XmlSupport.readAttributes(laneElement, lane.getAttributes());
                assignment.addLane(lane);
            }
            lanes.addAssignment(assignment);
        }
        return lanes;
    }

    private static void addLinkIds(Lane lane, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String id : value.split(",")) {
            if (!id.isBlank()) {
                lane.addToLinkId(Id.create(id.trim(), Link.class));
            }
        }
    }

    private static void addLaneIds(Lane lane, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String id : value.split(",")) {
            if (!id.isBlank()) {
                lane.addToLaneId(Id.create(id.trim(), Lane.class));
            }
        }
    }
}
