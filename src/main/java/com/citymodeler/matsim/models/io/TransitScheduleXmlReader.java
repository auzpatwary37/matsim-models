package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

public final class TransitScheduleXmlReader {
    private static final String SCHEMA = "/schemas/transitSchedule.xsd";
    private final boolean validateSchema;

    public TransitScheduleXmlReader() {
        this(false);
    }

    public TransitScheduleXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public TransitSchedule read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public TransitSchedule read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public TransitSchedule read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    private TransitSchedule read(Element root) {
        TransitSchedule schedule = new TransitSchedule();
        XmlSupport.readAttributes(root, schedule.getAttributes());

        Element stopsElement = XmlSupport.child(root, "transitStops");
        if (stopsElement != null) {
            for (Element stopElement : XmlSupport.children(stopsElement, "stopFacility")) {
                TransitStopFacility facility = new TransitStopFacility(
                        Id.create(XmlSupport.attr(stopElement, "id"), TransitStopFacility.class),
                        new Coord(XmlSupport.requiredDouble(stopElement, "x"), XmlSupport.requiredDouble(stopElement, "y")),
                        XmlSupport.optionalBoolean(stopElement, "isBlocking", false));
                String linkId = XmlSupport.attr(stopElement, "linkId");
                if (linkId != null && !linkId.isBlank()) {
                    facility.setLinkId(Id.create(linkId, Link.class));
                }
                facility.setName(XmlSupport.attr(stopElement, "name"));
                XmlSupport.readAttributes(stopElement, facility.getAttributes());
                schedule.addStopFacility(facility);
            }
        }

        for (Element lineElement : XmlSupport.children(root, "transitLine")) {
            TransitLine line = new TransitLine(Id.create(XmlSupport.attr(lineElement, "id"), TransitLine.class));
            line.setName(XmlSupport.attr(lineElement, "name"));
            XmlSupport.readAttributes(lineElement, line.getAttributes());
            for (Element routeElement : XmlSupport.children(lineElement, "transitRoute")) {
                TransitRoute route = new TransitRoute(Id.create(XmlSupport.attr(routeElement, "id"), TransitRoute.class));
                Element descriptionElement = XmlSupport.child(routeElement, "description");
                if (descriptionElement != null) {
                    route.setDescription(descriptionElement.getTextContent());
                }
                String transportMode = XmlSupport.attr(routeElement, "transportMode");
                if (transportMode == null || transportMode.isBlank()) {
                    Element modeElement = XmlSupport.child(routeElement, "transportMode");
                    if (modeElement != null) {
                        transportMode = modeElement.getTextContent();
                    }
                }
                if (transportMode != null && !transportMode.isBlank()) {
                    route.setTransportMode(transportMode);
                }
                XmlSupport.readAttributes(routeElement, route.getAttributes());
                Element profileElement = XmlSupport.child(routeElement, "routeProfile");
                if (profileElement != null) {
                    for (Element stopElement : XmlSupport.children(profileElement, "stop")) {
                        route.addStop(new TransitRouteStop(
                                Id.create(XmlSupport.attr(stopElement, "refId"), TransitStopFacility.class),
                                XmlSupport.optionalDouble(stopElement, "arrivalOffset", 0.0),
                                XmlSupport.optionalDouble(stopElement, "departureOffset", 0.0),
                                XmlSupport.optionalBoolean(stopElement, "awaitDeparture", false)));
                    }
                }
                Element departuresElement = XmlSupport.child(routeElement, "departures");
                if (departuresElement != null) {
                    for (Element departureElement : XmlSupport.children(departuresElement, "departure")) {
                        Departure departure = new Departure(
                                Id.create(XmlSupport.attr(departureElement, "id"), Departure.class),
                                XmlSupport.requiredDouble(departureElement, "departureTime"));
                        departure.setVehicleId(XmlSupport.attr(departureElement, "vehicleRefId"));
                        route.addDeparture(departure);
                    }
                }
                line.addRoute(route);
            }
            schedule.addTransitLine(line);
        }

        schedule.postProcess();
        return schedule;
    }
}
