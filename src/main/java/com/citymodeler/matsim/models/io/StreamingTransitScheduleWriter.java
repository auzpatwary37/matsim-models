package com.citymodeler.matsim.models.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

/**
 * StAX streaming transit schedule writer with the same element and attribute
 * emission order as {@link TransitScheduleXmlWriter} (child {@code transportMode},
 * {@code linkRefId}, and the {@code <route>} link sequence).
 */
public final class StreamingTransitScheduleWriter {
    public void write(TransitSchedule schedule, Path path) {
        try (OutputStream outputStream = XmlSupport.openOutputStream(path)) {
            write(schedule, outputStream);
        } catch (IOException exception) {
            throw new MatsimWriteException("Could not write XML to " + path, exception);
        }
    }

    public void write(TransitSchedule schedule, OutputStream outputStream) {
        XMLStreamWriter writer = null;
        try {
            writer = StreamingNetworkWriter.newFactory().createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name());
            writeDocument(schedule, writer);
        } catch (XMLStreamException exception) {
            throw new MatsimWriteException("Could not write transit schedule XML", exception);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (XMLStreamException exception) {
                    throw new MatsimWriteException("Could not close transit schedule XML stream", exception);
                }
            }
        }
    }

    public String writeToString(TransitSchedule schedule) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        write(schedule, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private void writeDocument(TransitSchedule schedule, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
        writer.writeStartElement("transitSchedule");
        StreamingNetworkWriter.writeAttributes(writer, schedule.getAttributes());

        writer.writeStartElement("transitStops");
        for (TransitStopFacility facility : schedule.getFacilities().values()) {
            writer.writeStartElement("stopFacility");
            writer.writeAttribute("id", facility.getId().toString());
            writer.writeAttribute("x", Double.toString(facility.getCoord().getX()));
            writer.writeAttribute("y", Double.toString(facility.getCoord().getY()));
            if (facility.getLinkId() != null) {
                writer.writeAttribute("linkRefId", facility.getLinkId().toString());
            }
            if (facility.getStopAreaId() != null) {
                writer.writeAttribute("stopAreaId", facility.getStopAreaId().toString());
            }
            if (facility.getName() != null) {
                writer.writeAttribute("name", facility.getName());
            }
            writer.writeAttribute("isBlocking", Boolean.toString(facility.isBlockingLane()));
            StreamingNetworkWriter.writeAttributes(writer, facility.getAttributes());
            writer.writeEndElement();
        }
        writer.writeEndElement();

        for (TransitLine line : schedule.getTransitLines().values()) {
            writer.writeStartElement("transitLine");
            writer.writeAttribute("id", line.getId().toString());
            if (line.getName() != null) {
                writer.writeAttribute("name", line.getName());
            }
            StreamingNetworkWriter.writeAttributes(writer, line.getAttributes());
            for (TransitRoute route : line.getRoutes().values()) {
                writer.writeStartElement("transitRoute");
                writer.writeAttribute("id", route.getId().toString());
                appendText(writer, "description", route.getDescription());
                appendText(writer, "transportMode", route.getTransportMode());
                StreamingNetworkWriter.writeAttributes(writer, route.getAttributes());

                writer.writeStartElement("routeProfile");
                for (TransitRouteStop stop : route.getStops()) {
                    writer.writeStartElement("stop");
                    writer.writeAttribute("refId", stop.getStopFacilityId().toString());
                    writer.writeAttribute("arrivalOffset", Double.toString(stop.getArrivalOffset()));
                    writer.writeAttribute("departureOffset", Double.toString(stop.getDepartureOffset()));
                    writer.writeAttribute("awaitDeparture", Boolean.toString(stop.isAwaitDeparture()));
                    writer.writeEndElement();
                }
                writer.writeEndElement();

                if (route.getNetworkRoute() != null) {
                    writer.writeStartElement("route");
                    for (var linkId : route.getNetworkRoute()) {
                        writer.writeStartElement("link");
                        writer.writeAttribute("refId", linkId.toString());
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                }

                writer.writeStartElement("departures");
                for (Departure departure : route.getDepartures().values()) {
                    writer.writeStartElement("departure");
                    writer.writeAttribute("id", departure.getId().toString());
                    writer.writeAttribute("departureTime", Double.toString(departure.getDepartureTime()));
                    if (departure.getVehicleId() != null) {
                        writer.writeAttribute("vehicleRefId", departure.getVehicleId());
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();

                writer.writeEndElement();
            }
            writer.writeEndElement();
        }

        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();
    }

    private static void appendText(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
        if (value == null) {
            return;
        }
        writer.writeStartElement(name);
        writer.writeCharacters(value);
        writer.writeEndElement();
    }
}
