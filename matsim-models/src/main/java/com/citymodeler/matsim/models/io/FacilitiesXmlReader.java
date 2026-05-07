package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.facilities.ActivityOption;
import com.citymodeler.matsim.models.network.Link;

public final class FacilitiesXmlReader {
    public ActivityFacilities read(Path path) {
        return read(XmlSupport.parse(path).getDocumentElement());
    }

    public ActivityFacilities read(InputStream inputStream) {
        return read(XmlSupport.parse(inputStream).getDocumentElement());
    }

    public ActivityFacilities read(String xml) {
        return read(XmlSupport.parse(xml).getDocumentElement());
    }

    private ActivityFacilities read(Element root) {
        ActivityFacilities facilities = new ActivityFacilities(XmlSupport.attr(root, "name"));
        XmlSupport.readAttributes(root, facilities.getAttributes());
        for (Element facilityElement : XmlSupport.children(root, "facility")) {
            ActivityFacility facility = new ActivityFacility(
                    Id.create(XmlSupport.attr(facilityElement, "id"), ActivityFacility.class),
                    new Coord(XmlSupport.requiredDouble(facilityElement, "x"), XmlSupport.requiredDouble(facilityElement, "y")));
            String linkId = XmlSupport.attr(facilityElement, "linkId");
            if (linkId != null && !linkId.isBlank()) {
                facility.setLinkId(Id.create(linkId, Link.class));
            }
            facility.setDesc(XmlSupport.attr(facilityElement, "desc"));
            XmlSupport.readAttributes(facilityElement, facility.getAttributes());
            for (Element activityElement : XmlSupport.children(facilityElement, "activity")) {
                ActivityOption option = new ActivityOption(XmlSupport.attr(activityElement, "type"));
                option.setCapacity(XmlSupport.optionalDouble(activityElement, "capacity", 0.0));
                facility.addActivityOption(option);
            }
            facilities.addFacility(facility);
        }
        return facilities;
    }
}
