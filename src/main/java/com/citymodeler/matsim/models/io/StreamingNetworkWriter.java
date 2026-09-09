package com.citymodeler.matsim.models.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

/**
 * StAX streaming network writer with the same element and attribute emission
 * order as {@link NetworkXmlWriter}; suitable for large networks where the DOM
 * writer's full in-memory document is wasteful.
 */
public final class StreamingNetworkWriter {
    private final Double capacityPeriodSeconds;

    public StreamingNetworkWriter() {
        this.capacityPeriodSeconds = null;
    }

    /** Emits {@code capperiod} on the {@code <links>} element using the given seconds. */
    public StreamingNetworkWriter(double capacityPeriodSeconds) {
        this.capacityPeriodSeconds = capacityPeriodSeconds;
    }

    public void write(Network network, Path path) {
        try (OutputStream outputStream = XmlSupport.openOutputStream(path)) {
            write(network, outputStream);
        } catch (IOException exception) {
            throw new MatsimWriteException("Could not write XML to " + path, exception);
        }
    }

    public void write(Network network, OutputStream outputStream) {
        XMLStreamWriter writer = null;
        try {
            writer = newFactory().createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name());
            writeDocument(network, writer);
        } catch (XMLStreamException exception) {
            throw new MatsimWriteException("Could not write network XML", exception);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (XMLStreamException exception) {
                    throw new MatsimWriteException("Could not close network XML stream", exception);
                }
            }
        }
    }

    public String writeToString(Network network) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        write(network, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private void writeDocument(Network network, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
        writer.writeStartElement("network");
        if (network.getName() != null) {
            writer.writeAttribute("name", network.getName());
        }
        writeAttributes(writer, network.getAttributes());

        writer.writeStartElement("nodes");
        for (Node node : network.getNodes().values()) {
            writer.writeStartElement("node");
            writer.writeAttribute("id", node.getId().toString());
            writer.writeAttribute("x", Double.toString(node.getCoord().getX()));
            writer.writeAttribute("y", Double.toString(node.getCoord().getY()));
            writeAttributes(writer, node.getAttributes());
            writer.writeEndElement();
        }
        writer.writeEndElement();

        writer.writeStartElement("links");
        if (capacityPeriodSeconds != null) {
            writer.writeAttribute("capperiod", Double.toString(capacityPeriodSeconds));
        }
        for (Link link : network.getLinks().values()) {
            writer.writeStartElement("link");
            writer.writeAttribute("id", link.getId().toString());
            writer.writeAttribute("from", link.getFromNodeId().toString());
            writer.writeAttribute("to", link.getToNodeId().toString());
            writer.writeAttribute("length", Double.toString(link.getLength()));
            writer.writeAttribute("capacity", Double.toString(link.getCapacity()));
            writer.writeAttribute("freespeed", Double.toString(link.getFreespeed()));
            writer.writeAttribute("permlanes", Double.toString(link.getNumberOfLanes()));
            StringBuilder modes = new StringBuilder();
            for (String mode : link.getAllowedModes()) {
                if (modes.length() > 0) {
                    modes.append(',');
                }
                modes.append(mode);
            }
            writer.writeAttribute("modes", modes.toString());
            writeAttributes(writer, link.getAttributes());
            writer.writeEndElement();
        }
        writer.writeEndElement();

        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();
    }

    static void writeAttributes(XMLStreamWriter writer, Attributes attributes) throws XMLStreamException {
        if (attributes.getAsMap().isEmpty()) {
            return;
        }
        writer.writeStartElement("attributes");
        for (Map.Entry<String, Object> entry : new TreeMap<>(attributes.getAsMap()).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            writer.writeStartElement("attribute");
            writer.writeAttribute("name", entry.getKey());
            writer.writeAttribute("class", XmlSupport.classHint(entry.getValue()));
            writer.writeCharacters(XmlSupport.textContent(entry.getValue()));
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    static XMLOutputFactory newFactory() {
        return XMLOutputFactory.newInstance();
    }
}
