package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.facilities.ActivityOption;

public final class FacilitiesXmlWriter {
    public void write(ActivityFacilities facilities, Path path) {
        XmlSupport.write(document(facilities), path);
    }

    public void write(ActivityFacilities facilities, OutputStream outputStream) {
        XmlSupport.write(document(facilities), outputStream);
    }

    public String writeToString(ActivityFacilities facilities) {
        return XmlSupport.writeToString(document(facilities));
    }

    private Document document(ActivityFacilities facilities) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElement("facilities");
        XmlSupport.setIfPresent(root, "name", facilities.getName());
        XmlSupport.appendAttributes(document, root, facilities.getAttributes());
        document.appendChild(root);
        for (ActivityFacility facility : facilities.getFacilities().values()) {
            Element facilityElement = document.createElement("facility");
            facilityElement.setAttribute("id", facility.getId().toString());
            facilityElement.setAttribute("x", Double.toString(facility.getCoord().getX()));
            facilityElement.setAttribute("y", Double.toString(facility.getCoord().getY()));
            XmlSupport.setIfPresent(facilityElement, "linkId", facility.getLinkId());
            XmlSupport.setIfPresent(facilityElement, "desc", facility.getDesc());
            XmlSupport.appendAttributes(document, facilityElement, facility.getAttributes());
            for (ActivityOption option : facility.getActivityOptions().values()) {
                Element optionElement = document.createElement("activity");
                optionElement.setAttribute("type", option.getType());
                optionElement.setAttribute("capacity", Double.toString(option.getCapacity()));
                facilityElement.appendChild(optionElement);
            }
            root.appendChild(facilityElement);
        }
        return document;
    }
}
