package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.vehicles.Vehicle;
import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;
import com.citymodeler.matsim.models.vehicles.VehicleType;

/**
 * Writes vehicle definitions in the current MATSim vehicleDefinitions v2.0
 * wire format: a {@code vehicleDefinitions} root in the
 * {@code http://www.matsim.org/files/dtd} namespace declaring the
 * {@code vehicleDefinitions_v2.0.xsd} schema location, {@code <vehicleType>}
 * elements first (with {@code seats}/{@code standingRoomInPersons} integer
 * capacity, {@code meter} dimension units, and per-person access/egress times
 * as vehicle-type attributes), followed by {@code <vehicle>} elements.
 *
 * <p>The element names and structure are defined by the published v2.0
 * schema; no MATSim or Pt2MATSim code is used.</p>
 */
public final class VehiclesXmlWriter {
    static final String MATSIM_NAMESPACE = "http://www.matsim.org/files/dtd";
    static final String V2_SCHEMA_LOCATION =
            MATSIM_NAMESPACE + " " + MATSIM_NAMESPACE + "/vehicleDefinitions_v2.0.xsd";
    static final String ACCESS_TIME_IN_SECONDS_PER_PERSON = "accessTimeInSecondsPerPerson";
    static final String EGRESS_TIME_IN_SECONDS_PER_PERSON = "egressTimeInSecondsPerPerson";

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
        Element root = document.createElementNS(MATSIM_NAMESPACE, "vehicleDefinitions");
        root.setAttributeNS(
                "http://www.w3.org/2001/XMLSchema-instance",
                "xsi:schemaLocation",
                V2_SCHEMA_LOCATION);
        document.appendChild(root);

        for (VehicleType type : definitions.getVehicleTypes().values()) {
            Element typeElement = document.createElementNS(MATSIM_NAMESPACE, "vehicleType");
            typeElement.setAttribute("id", type.getId().toString());
            emitVehicleTypeChildrenInSchemaOrder(document, typeElement, type);
            root.appendChild(typeElement);
        }

        for (Vehicle vehicle : definitions.getVehicles().values()) {
            Element vehicleElement = document.createElementNS(MATSIM_NAMESPACE, "vehicle");
            vehicleElement.setAttribute("id", vehicle.getId().toString());
            vehicleElement.setAttribute("type", vehicle.getType());
            XmlSupport.appendAttributes(document, vehicleElement, vehicle.getAttributes(), MATSIM_NAMESPACE);
            root.appendChild(vehicleElement);
        }
        return document;
    }

    private static final java.util.Map<String, Integer> V2_CHILD_ORDER = java.util.Map.ofEntries(
            java.util.Map.entry("attributes", 0),
            java.util.Map.entry("description", 1),
            java.util.Map.entry("capacity", 2),
            java.util.Map.entry("length", 3),
            java.util.Map.entry("width", 4),
            java.util.Map.entry("maximumVelocity", 5),
            java.util.Map.entry("engineInformation", 6),
            java.util.Map.entry("costInformation", 7),
            java.util.Map.entry("passengerCarEquivalents", 8),
            java.util.Map.entry("networkMode", 9),
            java.util.Map.entry("flowEfficiencyFactor", 10)
    );

    private void emitVehicleTypeChildrenInSchemaOrder(Document document, Element typeElement, VehicleType type) {
        record OrderedEntry(int position, java.util.function.Consumer<Document> emitter) { }
        var entries = new java.util.ArrayList<OrderedEntry>();

        // Position 0: attributes
        Attributes vehicleTypeAttributes = new Attributes();
        for (var entry : type.getExtraAttributes().getAsMap().entrySet()) {
            vehicleTypeAttributes.putAttribute(entry.getKey(), entry.getValue());
        }
        if (type.getAccessTimeSeconds() != null) {
            vehicleTypeAttributes.putAttribute(ACCESS_TIME_IN_SECONDS_PER_PERSON, type.getAccessTimeSeconds());
        }
        if (type.getEgressTimeSeconds() != null) {
            vehicleTypeAttributes.putAttribute(EGRESS_TIME_IN_SECONDS_PER_PERSON, type.getEgressTimeSeconds());
        }
        if (!vehicleTypeAttributes.getAsMap().isEmpty()) {
            entries.add(new OrderedEntry(0, doc ->
                    XmlSupport.appendAttributes(doc, typeElement, vehicleTypeAttributes, MATSIM_NAMESPACE)));
        }

        // Position 2: capacity
        if (type.getSeatingCapacity() != null || type.getStandingCapacity() != null
                || type.getCapacityVolumeInCubicMeters() != null || type.getCapacityWeightInTons() != null
                || !type.getCapacityExtraAttributes().getAsMap().isEmpty()) {
            entries.add(new OrderedEntry(2, doc -> {
                Element capacityElement = doc.createElementNS(MATSIM_NAMESPACE, "capacity");
                if (type.getSeatingCapacity() != null) {
                    capacityElement.setAttribute("seats", Integer.toString(type.getSeatingCapacity()));
                }
                if (type.getStandingCapacity() != null) {
                    capacityElement.setAttribute("standingRoomInPersons", Integer.toString(type.getStandingCapacity()));
                }
                if (type.getCapacityVolumeInCubicMeters() != null) {
                    capacityElement.setAttribute("volumeInCubicMeters", type.getCapacityVolumeInCubicMeters());
                }
                if (type.getCapacityWeightInTons() != null) {
                    capacityElement.setAttribute("weightInTons", type.getCapacityWeightInTons());
                }
                if (!type.getCapacityExtraAttributes().getAsMap().isEmpty()) {
                    XmlSupport.appendAttributes(doc, capacityElement, type.getCapacityExtraAttributes(), MATSIM_NAMESPACE);
                }
                typeElement.appendChild(capacityElement);
            }));
        }

        // Position 3: length
        if (type.getLengthMeters() > 0.0) {
            entries.add(new OrderedEntry(3, doc -> {
                Element el = doc.createElementNS(MATSIM_NAMESPACE, "length");
                el.setAttribute("meter", Double.toString(type.getLengthMeters()));
                typeElement.appendChild(el);
            }));
        }

        // Position 4: width
        if (type.getWidthMeters() > 0.0) {
            entries.add(new OrderedEntry(4, doc -> {
                Element el = doc.createElementNS(MATSIM_NAMESPACE, "width");
                el.setAttribute("meter", Double.toString(type.getWidthMeters()));
                typeElement.appendChild(el);
            }));
        }

        // Extension elements at their schema positions
        for (var ext : type.getExtensionElements()) {
            int pos = V2_CHILD_ORDER.getOrDefault(ext.tagName(), 11);
            entries.add(new OrderedEntry(pos, doc ->
                    XmlSupport.appendRawXmlFragment(doc, typeElement, ext.serializedXml())));
        }

        entries.sort(java.util.Comparator.comparingInt(OrderedEntry::position));
        for (var entry : entries) {
            entry.emitter().accept(document);
        }
    }
}
