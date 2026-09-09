package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.vehicles.Vehicle;
import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;
import com.citymodeler.matsim.models.vehicles.VehicleType;

public final class VehiclesXmlReader {
    private static final String SCHEMA = "/schemas/vehicles.xsd";
    private final boolean validateSchema;

    public VehiclesXmlReader() {
        this(false);
    }

    public VehiclesXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public VehicleDefinitions read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public VehicleDefinitions read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public VehicleDefinitions read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    private VehicleDefinitions read(Element root) {
        VehicleDefinitions definitions = new VehicleDefinitions();
        for (Element typeElement : XmlSupport.children(root, "vehicleType")) {
            VehicleType type = new VehicleType(Id.create(XmlSupport.attr(typeElement, "id"), VehicleType.class));
            Element capacityElement = XmlSupport.child(typeElement, "capacity");
            if (capacityElement != null) {
                type.setSeatingCapacity(XmlSupport.optionalDouble(capacityElement, "seats", 0.0));
                type.setStandingCapacity(XmlSupport.optionalDouble(capacityElement, "standingRoom", 0.0));
            }
            Element accessTimeElement = XmlSupport.child(typeElement, "accessTime");
            if (accessTimeElement != null) {
                type.setAccessTimeSeconds(XmlSupport.optionalDouble(accessTimeElement, "seconds", 0.0));
            }
            Element egressTimeElement = XmlSupport.child(typeElement, "egressTime");
            if (egressTimeElement != null) {
                type.setEgressTimeSeconds(XmlSupport.optionalDouble(egressTimeElement, "seconds", 0.0));
            }
            Element lengthElement = XmlSupport.child(typeElement, "length");
            if (lengthElement != null) {
                type.setLengthMeters(XmlSupport.optionalDouble(lengthElement, "meters", 0.0));
            }
            Element widthElement = XmlSupport.child(typeElement, "width");
            if (widthElement != null) {
                type.setWidthMeters(XmlSupport.optionalDouble(widthElement, "meters", 0.0));
            }
            definitions.addVehicleType(type);
        }
        for (Element vehicleElement : XmlSupport.children(root, "vehicle")) {
            Vehicle vehicle = new Vehicle(
                    Id.create(XmlSupport.attr(vehicleElement, "id"), Vehicle.class),
                    XmlSupport.attr(vehicleElement, "type"));
            XmlSupport.readAttributes(vehicleElement, vehicle.getAttributes());
            definitions.addVehicle(vehicle);
        }
        return definitions;
    }
}
