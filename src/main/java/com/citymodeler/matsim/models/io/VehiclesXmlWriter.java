package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.vehicles.Vehicle;
import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;
import com.citymodeler.matsim.models.vehicles.VehicleType;

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
        for (VehicleType type : definitions.getVehicleTypes().values()) {
            Element typeElement = document.createElement("vehicleType");
            typeElement.setAttribute("id", type.getId().toString());

            Element capacityElement = document.createElement("capacity");
            capacityElement.setAttribute("seats", Double.toString(type.getSeatingCapacity()));
            capacityElement.setAttribute("standingRoom", Double.toString(type.getStandingCapacity()));
            capacityElement.setAttribute("persons", Double.toString(type.getSeatingCapacity() + type.getStandingCapacity()));
            typeElement.appendChild(capacityElement);

            if (type.getAccessTimeSeconds() > 0.0) {
                Element accessTimeElement = document.createElement("accessTime");
                accessTimeElement.setAttribute("seconds", Double.toString(type.getAccessTimeSeconds()));
                typeElement.appendChild(accessTimeElement);
            }
            if (type.getEgressTimeSeconds() > 0.0) {
                Element egressTimeElement = document.createElement("egressTime");
                egressTimeElement.setAttribute("seconds", Double.toString(type.getEgressTimeSeconds()));
                typeElement.appendChild(egressTimeElement);
            }
            if (type.getLengthMeters() > 0.0) {
                Element lengthElement = document.createElement("length");
                lengthElement.setAttribute("meters", Double.toString(type.getLengthMeters()));
                typeElement.appendChild(lengthElement);
            }
            if (type.getWidthMeters() > 0.0) {
                Element widthElement = document.createElement("width");
                widthElement.setAttribute("meters", Double.toString(type.getWidthMeters()));
                typeElement.appendChild(widthElement);
            }
            root.appendChild(typeElement);
        }
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