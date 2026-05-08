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
    private static final String SCHEMA = "/schemas/facilities.xsd";
    private final boolean validateSchema;

    public FacilitiesXmlReader() {
        this(false);
    }

    public FacilitiesXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public ActivityFacilities read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public ActivityFacilities read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public ActivityFacilities read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
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
