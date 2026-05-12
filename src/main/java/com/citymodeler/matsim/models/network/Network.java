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
        if (nodes.containsKey(node.getId())) {
            throw new IllegalArgumentException("Node " + node.getId() + " already exists in network");
        }
        nodes.put(node.getId(), node);
    }

    public void addLink(Link link) {
        Objects.requireNonNull(link, "link");
        if (links.containsKey(link.getId())) {
            throw new IllegalArgumentException("Link " + link.getId() + " already exists in network");
        }
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
        Id<Link> lid = Id.create(linkId, Link.class);
        Id<Node> fromId = Id.create(fromNodeId, Node.class);
        Id<Node> toId = Id.create(toNodeId, Node.class);
        Link link = new Link(lid, fromId, toId, length, capacity, freespeed, numberOfLanes, allowedModes);
        addLink(link);
        wireLink(link);
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
        // Fallback: remove any links that still reference this node by endpoint ID
        for (Link link : new LinkedHashMap<>(links).values()) {
            if (link.getFromNodeId().equals(nodeId) || link.getToNodeId().equals(nodeId)) {
                removeLink(link.getId());
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

    private void wireLink(Link link) {
        Node fromNode = nodes.get(link.getFromNodeId());
        Node toNode = nodes.get(link.getToNodeId());
        if (fromNode != null) {
            link.setFromNode(fromNode);
            fromNode.outLinks().put(link.getId(), link);
        }
        if (toNode != null) {
            link.setToNode(toNode);
            toNode.inLinks().put(link.getId(), link);
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
