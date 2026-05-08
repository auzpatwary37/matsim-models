package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

public final class TransitScheduleXmlWriter {
    public void write(TransitSchedule schedule, Path path) {
        XmlSupport.write(document(schedule), path);
    }

    public void write(TransitSchedule schedule, OutputStream outputStream) {
        XmlSupport.write(document(schedule), outputStream);
    }

    public String writeToString(TransitSchedule schedule) {
        return XmlSupport.writeToString(document(schedule));
    }

    private Document document(TransitSchedule schedule) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElement("transitSchedule");
        document.appendChild(root);
        XmlSupport.appendAttributes(document, root, schedule.getAttributes());

        Element stopsElement = document.createElement("transitStops");
        root.appendChild(stopsElement);
        for (TransitStopFacility facility : schedule.getFacilities().values()) {
            Element stopElement = document.createElement("stopFacility");
            stopElement.setAttribute("id", facility.getId().toString());
            stopElement.setAttribute("x", Double.toString(facility.getCoord().getX()));
            stopElement.setAttribute("y", Double.toString(facility.getCoord().getY()));
            XmlSupport.setIfPresent(stopElement, "linkId", facility.getLinkId());
            XmlSupport.setIfPresent(stopElement, "name", facility.getName());
            stopElement.setAttribute("isBlocking", Boolean.toString(facility.isBlockingLane()));
            XmlSupport.appendAttributes(document, stopElement, facility.getAttributes());
            stopsElement.appendChild(stopElement);
        }

        for (TransitLine line : schedule.getTransitLines().values()) {
            Element lineElement = document.createElement("transitLine");
            lineElement.setAttribute("id", line.getId().toString());
            XmlSupport.setIfPresent(lineElement, "name", line.getName());
            XmlSupport.appendAttributes(document, lineElement, line.getAttributes());
            for (TransitRoute route : line.getRoutes().values()) {
                Element routeElement = document.createElement("transitRoute");
                routeElement.setAttribute("id", route.getId().toString());
                if (route.getTransportMode() != null) {
                    routeElement.setAttribute("transportMode", route.getTransportMode());
                }
                appendText(document, routeElement, "description", route.getDescription());
                XmlSupport.appendAttributes(document, routeElement, route.getAttributes());

                Element profileElement = document.createElement("routeProfile");
                routeElement.appendChild(profileElement);
                for (TransitRouteStop stop : route.getStops()) {
                    Element stopElement = document.createElement("stop");
                    stopElement.setAttribute("refId", stop.getStopFacilityId().toString());
                    stopElement.setAttribute("arrivalOffset", Double.toString(stop.getArrivalOffset()));
                    stopElement.setAttribute("departureOffset", Double.toString(stop.getDepartureOffset()));
                    stopElement.setAttribute("awaitDeparture", Boolean.toString(stop.isAwaitDeparture()));
                    profileElement.appendChild(stopElement);
                }

                Element departuresElement = document.createElement("departures");
                routeElement.appendChild(departuresElement);
                for (Departure departure : route.getDepartures().values()) {
                    Element departureElement = document.createElement("departure");
                    departureElement.setAttribute("id", departure.getId().toString());
                    departureElement.setAttribute("departureTime", Double.toString(departure.getDepartureTime()));
                    XmlSupport.setIfPresent(departureElement, "vehicleRefId", departure.getVehicleId());
                    departuresElement.appendChild(departureElement);
                }

                lineElement.appendChild(routeElement);
            }
            root.appendChild(lineElement);
        }
        return document;
    }

    private static void appendText(Document document, Element parent, String name, String value) {
        if (value == null) {
            return;
        }
        Element element = document.createElement(name);
        element.setTextContent(value);
        parent.appendChild(element);
    }
}
