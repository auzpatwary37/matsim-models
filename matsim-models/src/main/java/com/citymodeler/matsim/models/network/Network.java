package com.citymodeler.matsim.models.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
        return nodes;
    }

    public Map<Id<Link>, Link> getLinks() {
        return links;
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

    public void postProcess() {
        for (Node node : nodes.values()) {
            node.getInLinks().clear();
            node.getOutLinks().clear();
        }

        for (Link link : links.values()) {
            Node fromNode = requireNode(link, link.getFromNodeId());
            Node toNode = requireNode(link, link.getToNodeId());

            link.setFromNode(fromNode);
            link.setToNode(toNode);
            fromNode.getOutLinks().put(link.getId(), link);
            toNode.getInLinks().put(link.getId(), link);
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
