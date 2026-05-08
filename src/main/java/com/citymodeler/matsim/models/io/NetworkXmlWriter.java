package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.StringJoiner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

public final class NetworkXmlWriter {
    public void write(Network network, Path path) {
        XmlSupport.write(document(network), path);
    }

    public void write(Network network, OutputStream outputStream) {
        XmlSupport.write(document(network), outputStream);
    }

    public String writeToString(Network network) {
        return XmlSupport.writeToString(document(network));
    }

    private Document document(Network network) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElement("network");
        XmlSupport.setIfPresent(root, "name", network.getName());
        document.appendChild(root);
        XmlSupport.appendAttributes(document, root, network.getAttributes());

        Element nodes = document.createElement("nodes");
        root.appendChild(nodes);
        for (Node node : network.getNodes().values()) {
            Element nodeElement = document.createElement("node");
            nodeElement.setAttribute("id", node.getId().toString());
            nodeElement.setAttribute("x", Double.toString(node.getCoord().getX()));
            nodeElement.setAttribute("y", Double.toString(node.getCoord().getY()));
            XmlSupport.appendAttributes(document, nodeElement, node.getAttributes());
            nodes.appendChild(nodeElement);
        }

        Element links = document.createElement("links");
        root.appendChild(links);
        for (Link link : network.getLinks().values()) {
            Element linkElement = document.createElement("link");
            linkElement.setAttribute("id", link.getId().toString());
            linkElement.setAttribute("from", link.getFromNodeId().toString());
            linkElement.setAttribute("to", link.getToNodeId().toString());
            linkElement.setAttribute("length", Double.toString(link.getLength()));
            linkElement.setAttribute("capacity", Double.toString(link.getCapacity()));
            linkElement.setAttribute("freespeed", Double.toString(link.getFreespeed()));
            linkElement.setAttribute("permlanes", Double.toString(link.getNumberOfLanes()));
            if (!link.getAllowedModes().isEmpty()) {
                StringJoiner joiner = new StringJoiner(",");
                link.getAllowedModes().forEach(joiner::add);
                linkElement.setAttribute("modes", joiner.toString());
            }
            XmlSupport.appendAttributes(document, linkElement, link.getAttributes());
            links.appendChild(linkElement);
        }
        return document;
    }
}
