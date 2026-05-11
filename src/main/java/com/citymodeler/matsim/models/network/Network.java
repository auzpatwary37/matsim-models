package com.citymodeler.matsim.models.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class Network {
    private String name;
    private final Map<Id<Node>, Node> nodes = new LinkedHashMap<>();
    private final Map<Id<Link>, Link> links = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public Network() {
    }

    public Network(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Id<Node>, Node> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public Map<Id<Link>, Link> getLinks() {
        return Collections.unmodifiableMap(links);
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void addNode(Node node) {
        Objects.requireNonNull(node, "node");
        nodes.put(node.getId(), node);
    }

    public void addLink(Link link) {
        Objects.requireNonNull(link, "link");
        links.put(link.getId(), link);
    }

    public Node createNode(String nodeId, double x, double y) {
        Node node = new Node(Id.create(nodeId, Node.class), new com.citymodeler.matsim.models.api.Coord(x, y));
        addNode(node);
        return node;
    }

    public Link createLink(
            String linkId, String fromNodeId, String toNodeId,
            double length, double capacity, double freespeed, double numberOfLanes, Set<String> allowedModes) {
        Link link = new Link(
            Id.create(linkId, Link.class),
            Id.create(fromNodeId, Node.class),
            Id.create(toNodeId, Node.class),
            length, capacity, freespeed, numberOfLanes, allowedModes);
        addLink(link);
        return link;
    }

    public void removeNode(Id<Node> nodeId) {
        Node node = nodes.remove(nodeId);
        if (node != null) {
            for (Link inLink : new LinkedHashMap<>(node.inLinks()).values()) {
                removeLink(inLink.getId());
            }
            for (Link outLink : new LinkedHashMap<>(node.outLinks()).values()) {
                removeLink(outLink.getId());
            }
        }
    }

    public void removeLink(Id<Link> linkId) {
        Link link = links.remove(linkId);
        if (link != null) {
            Node fromNode = link.getFromNode();
            Node toNode = link.getToNode();
            if (fromNode != null) {
                fromNode.outLinks().remove(linkId);
            }
            if (toNode != null) {
                toNode.inLinks().remove(linkId);
            }
        }
    }

    public void postProcess() {
        for (Node node : nodes.values()) {
            node.inLinks().clear();
            node.outLinks().clear();
        }

        for (Link link : links.values()) {
            Node fromNode = requireNode(link, link.getFromNodeId());
            Node toNode = requireNode(link, link.getToNodeId());

            link.setFromNode(fromNode);
            link.setToNode(toNode);
            fromNode.outLinks().put(link.getId(), link);
            toNode.inLinks().put(link.getId(), link);
        }
    }

    private Node requireNode(Link link, Id<Node> nodeId) {
        Node node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Link " + link.getId() + " references missing node " + nodeId);
        }
        return node;
    }
}
