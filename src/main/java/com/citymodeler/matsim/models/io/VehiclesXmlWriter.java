package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.vehicles.Vehicle;
import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;

public final class VehiclesXmlWriter {
    public void write(VehicleDefinitions definitions, Path path) {
        XmlSupport.write(document(definitions), path);
    }

    public void write(VehicleDefinitions definitions, OutputStream outputStream) {
        XmlSupport.write(document(definitions), outputStream);
    }

    public String writeToString(VehicleDefinitions definitions) {
        return XmlSupport.writeToString(document(definitions));
    }

    private Document document(VehicleDefinitions definitions) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElement("vehicles");
        document.appendChild(root);
        for (Vehicle vehicle : definitions.getVehicles().values()) {
            Element vehicleElement = document.createElement("vehicle");
            vehicleElement.setAttribute("id", vehicle.getId().toString());
            vehicleElement.setAttribute("type", vehicle.getType());
            XmlSupport.appendAttributes(document, vehicleElement, vehicle.getAttributes());
            root.appendChild(vehicleElement);
        }
        return document;
    }
}