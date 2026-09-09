package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.vehicles.Vehicle;
import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;
import com.citymodeler.matsim.models.vehicles.VehicleType;

/**
 * Reads vehicle definitions. The canonical input is the current MATSim
 * vehicleDefinitions v2.0 format ({@code vehicleDefinitions} root); the
 * historic repo-local {@code <vehicles>} dialect remains readable, including
 * its {@code standingRoom}/{@code persons} capacity spelling, {@code meters}
 * dimension unit, and {@code accessTime}/{@code egressTime} element form.
 */
public final class VehiclesXmlReader {
    private static final String SCHEMA = "/schemas/v2/vehicleDefinitions_v2.0.xsd";
    private static final String LEGACY_SCHEMA = "/schemas/vehicles.xsd";
    private final boolean validateSchema;

    public VehiclesXmlReader() {
        this(false);
    }

    public VehiclesXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public VehicleDefinitions read(Path path) {
        String schema = resolveSchema();
        return read((validateSchema ? XmlSupport.parse(path, schema) : XmlSupport.parse(path)).getDocumentElement());
    }

    public VehicleDefinitions read(InputStream inputStream) {
        String schema = resolveSchema();
        return read((validateSchema ? XmlSupport.parse(inputStream, schema) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public VehicleDefinitions read(String xml) {
        String schema = resolveSchema();
        return read((validateSchema ? XmlSupport.parse(xml, schema) : XmlSupport.parse(xml)).getDocumentElement());
    }

    private String resolveSchema() {
        return SCHEMA;
    }

    private VehicleDefinitions read(Element root) {
        VehicleDefinitions definitions = new VehicleDefinitions();
        for (Element typeElement : XmlSupport.children(root, "vehicleType")) {
            VehicleType type = new VehicleType(Id.create(XmlSupport.attr(typeElement, "id"), VehicleType.class));

            Element attributesElement = XmlSupport.child(typeElement, "attributes");
            if (attributesElement != null) {
                for (Element attributeElement : XmlSupport.children(attributesElement, "attribute")) {
                    String name = XmlSupport.attr(attributeElement, "name");
                    String text = attributeElement.getTextContent();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    if (VehiclesXmlWriter.ACCESS_TIME_IN_SECONDS_PER_PERSON.equals(name)) {
                        type.setAccessTimeSeconds(Double.parseDouble(text.trim()));
                    } else if (VehiclesXmlWriter.EGRESS_TIME_IN_SECONDS_PER_PERSON.equals(name)) {
                        type.setEgressTimeSeconds(Double.parseDouble(text.trim()));
                    } else {
                        String classAttr = XmlSupport.attr(attributeElement, "class");
                        Object value = coerceAttributeValue(classAttr, text.trim());
                        type.getExtraAttributes().putAttribute(name, value);
                    }
                }
            }
            Element accessTimeElement = XmlSupport.child(typeElement, "accessTime");
            if (accessTimeElement != null && type.getAccessTimeSeconds() == null) {
                type.setAccessTimeSeconds(XmlSupport.optionalDouble(accessTimeElement, "seconds", 0.0));
            }
            Element egressTimeElement = XmlSupport.child(typeElement, "egressTime");
            if (egressTimeElement != null && type.getEgressTimeSeconds() == null) {
                type.setEgressTimeSeconds(XmlSupport.optionalDouble(egressTimeElement, "seconds", 0.0));
            }

            Element capacityElement = XmlSupport.child(typeElement, "capacity");
            if (capacityElement != null) {
                String seats = XmlSupport.attr(capacityElement, "seats");
                if (seats != null && !seats.isBlank()) {
                    type.setSeatingCapacity(Integer.parseInt(seats.trim()));
                }
                String standing = XmlSupport.attr(capacityElement, "standingRoomInPersons");
                if (standing == null || standing.isBlank()) {
                    standing = XmlSupport.attr(capacityElement, "standingRoom");
                }
                if (standing != null && !standing.isBlank()) {
                    type.setStandingCapacity(Integer.parseInt(standing.trim()));
                }
                String volume = XmlSupport.attr(capacityElement, "volumeInCubicMeters");
                if (volume != null && !volume.isBlank()) {
                    type.setCapacityVolumeInCubicMeters(volume.trim());
                }
                String weight = XmlSupport.attr(capacityElement, "weightInTons");
                if (weight != null && !weight.isBlank()) {
                    type.setCapacityWeightInTons(weight.trim());
                }
                Element capAttrsEl = XmlSupport.child(capacityElement, "attributes");
                if (capAttrsEl != null) {
                    for (Element attrEl : XmlSupport.children(capAttrsEl, "attribute")) {
                        String name = XmlSupport.attr(attrEl, "name");
                        String text = attrEl.getTextContent();
                        String classAttr = XmlSupport.attr(attrEl, "class");
                        if (name != null && text != null && !text.isBlank()) {
                            type.getCapacityExtraAttributes().putAttribute(name, coerceAttributeValue(classAttr, text.trim()));
                        }
                    }
                }
            }

            Element lengthElement = XmlSupport.child(typeElement, "length");
            if (lengthElement != null) {
                type.setLengthMeters(optionalDimension(lengthElement));
            }
            Element widthElement = XmlSupport.child(typeElement, "width");
            if (widthElement != null) {
                type.setWidthMeters(optionalDimension(widthElement));
            }

            // Preserve unrecognized v2.0 vehicleType child elements losslessly.
            var knownElements = Set.of("attributes", "capacity", "length", "width", "accessTime", "egressTime");
            org.w3c.dom.NodeList childNodes = typeElement.getChildNodes();
            for (int ci = 0; ci < childNodes.getLength(); ci++) {
                if (childNodes.item(ci) instanceof Element childEl && !knownElements.contains(childEl.getTagName())) {
                    type.addExtensionElement(childEl.getTagName(), XmlSupport.writeElementToString(childEl));
                }
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

    /** Accepts the canonical {@code meter} unit as well as the historic {@code meters} spelling. */
    private static double optionalDimension(Element element) {
        String meter = XmlSupport.attr(element, "meter");
        if (meter != null && !meter.isBlank()) {
            return Double.parseDouble(meter.trim());
        }
        String legacyMeters = XmlSupport.attr(element, "meters");
        if (legacyMeters != null && !legacyMeters.isBlank()) {
            return Double.parseDouble(legacyMeters.trim());
        }
        return 0.0;
    }

    private static Object coerceAttributeValue(String classAttr, String text) {
        if (classAttr == null || classAttr.isBlank()) {
            return text;
        }
        try {
            if ("java.lang.Double".equals(classAttr) || "double".equals(classAttr)) {
                return Double.parseDouble(text);
            }
            if ("java.lang.Integer".equals(classAttr) || "int".equals(classAttr)) {
                return Integer.parseInt(text);
            }
            if ("java.lang.Boolean".equals(classAttr) || "boolean".equals(classAttr)) {
                return Boolean.parseBoolean(text);
            }
        } catch (NumberFormatException e) {
            return text;
        }
        return text;
    }
}
