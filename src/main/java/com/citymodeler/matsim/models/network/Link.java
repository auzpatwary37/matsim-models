package com.citymodeler.matsim.models.network;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class Link {
    private final Id<Link> id;
    private final Id<Node> fromNodeId;
    private final Id<Node> toNodeId;
    private Node fromNode;
    private Node toNode;
    private final double length;
    private final double capacity;
    private final double freespeed;
    private final double numberOfLanes;
    private final Set<String> allowedModes;
    private final Attributes attributes = new Attributes();

    public Link(
            Id<Link> id,
            Id<Node> fromNodeId,
            Id<Node> toNodeId,
            double length,
            double capacity,
            double freespeed,
            double numberOfLanes,
            Set<String> allowedModes) {
        this.id = Objects.requireNonNull(id, "id");
        this.fromNodeId = Objects.requireNonNull(fromNodeId, "fromNodeId");
        this.toNodeId = Objects.requireNonNull(toNodeId, "toNodeId");
        this.length = length;
        this.capacity = capacity;
        this.freespeed = freespeed;
        this.numberOfLanes = numberOfLanes;
        this.allowedModes = allowedModes == null || allowedModes.isEmpty()
                ? new LinkedHashSet<>(Set.of("car"))
                : new LinkedHashSet<>(allowedModes);
    }

    public Id<Link> getId() {
        return id;
    }

    public Node getFromNode() {
        return fromNode;
    }

    public Node getToNode() {
        return toNode;
    }

    public Id<Node> getFromNodeId() {
        return fromNodeId;
    }

    public Id<Node> getToNodeId() {
        return toNodeId;
    }

    public double getLength() {
        return length;
    }

    public double getCapacity() {
        return capacity;
    }

    public double getFreespeed() {
        return freespeed;
    }

    public double getNumberOfLanes() {
        return numberOfLanes;
    }

    public Set<String> getAllowedModes() {
        return allowedModes;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    void setFromNode(Node fromNode) {
        this.fromNode = fromNode;
    }

    void setToNode(Node toNode) {
        this.toNode = toNode;
    }
}
