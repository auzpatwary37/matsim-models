package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

public final class NetworkXmlReader {
    public Network read(Path path) {
        return read(XmlSupport.parse(path).getDocumentElement());
    }

    public Network read(InputStream inputStream) {
        return read(XmlSupport.parse(inputStream).getDocumentElement());
    }

    public Network read(String xml) {
        return read(XmlSupport.parse(xml).getDocumentElement());
    }

    private Network read(Element root) {
        Network network = new Network(XmlSupport.attr(root, "name"));
        XmlSupport.readAttributes(root, network.getAttributes());

        Element nodesElement = XmlSupport.child(root, "nodes");
        if (nodesElement != null) {
            for (Element nodeElement : XmlSupport.children(nodesElement, "node")) {
                Node node = new Node(
                        Id.create(XmlSupport.attr(nodeElement, "id"), Node.class),
                        new Coord(XmlSupport.requiredDouble(nodeElement, "x"), XmlSupport.requiredDouble(nodeElement, "y")));
                XmlSupport.readAttributes(nodeElement, node.getAttributes());
                network.addNode(node);
            }
        }

        Element linksElement = XmlSupport.child(root, "links");
        if (linksElement != null) {
            for (Element linkElement : XmlSupport.children(linksElement, "link")) {
                Link link = new Link(
                        Id.create(XmlSupport.attr(linkElement, "id"), Link.class),
                        Id.create(XmlSupport.attr(linkElement, "from"), Node.class),
                        Id.create(XmlSupport.attr(linkElement, "to"), Node.class),
                        XmlSupport.requiredDouble(linkElement, "length"),
                        XmlSupport.requiredDouble(linkElement, "capacity"),
                        XmlSupport.requiredDouble(linkElement, "freespeed"),
                        XmlSupport.requiredDouble(linkElement, "permlanes"),
                        modes(XmlSupport.attr(linkElement, "modes")));
                XmlSupport.readAttributes(linkElement, link.getAttributes());
                network.addLink(link);
            }
        }

        network.postProcess();
        return network;
    }

    private static Set<String> modes(String modes) {
        if (modes == null || modes.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Arrays.stream(modes.split(","))
                .map(String::trim)
                .filter(mode -> !mode.isBlank())
                .forEach(result::add);
        return result;
    }
}
