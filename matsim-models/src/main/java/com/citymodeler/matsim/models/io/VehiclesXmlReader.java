package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.vehicles.Vehicle;
import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;

public final class VehiclesXmlReader {
    public VehicleDefinitions read(Path path) {
        return read(XmlSupport.parse(path).getDocumentElement());
    }

    public VehicleDefinitions read(InputStream inputStream) {
        return read(XmlSupport.parse(inputStream).getDocumentElement());
    }

    public VehicleDefinitions read(String xml) {
        return read(XmlSupport.parse(xml).getDocumentElement());
    }

    private VehicleDefinitions read(Element root) {
        VehicleDefinitions definitions = new VehicleDefinitions();
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