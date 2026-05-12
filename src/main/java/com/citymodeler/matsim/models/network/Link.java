package com.citymodeler.matsim.models.network;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class Link {
    private final Id<Link> id;
    private final Id<Node> fromNodeId;
    private final Id<Node> toNodeId;
    private Node fromNode;
    private Node toNode;
    private double length;
    private double capacity;
    private double freespeed;
    private double numberOfLanes;
    private Set<String> allowedModes;
    private final Attributes attributes = new Attributes();

    public Link(
            Id<Link> id,
            Id<Node> fromNodeId,
            Id<Node> toNodeId,
            double length,
            double freespeed,
            double capacity,
            double numberOfLanes) {
        this(id, fromNodeId, toNodeId, length, freespeed, capacity, numberOfLanes, null);
    }

    public Link(
            Id<Link> id,
            Id<Node> fromNodeId,
            Id<Node> toNodeId,
            double length,
            double freespeed,
            double capacity,
            double numberOfLanes,
            Set<String> allowedModes) {
        this.id = Objects.requireNonNull(id, "id");
        this.fromNodeId = Objects.requireNonNull(fromNodeId, "fromNodeId");
        this.toNodeId = Objects.requireNonNull(toNodeId, "toNodeId");
        this.length = length;
        this.freespeed = freespeed;
        this.capacity = capacity;
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
        return Collections.unmodifiableSet(allowedModes);
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

    public void setLength(double length) {
        this.length = length;
    }

    public void setCapacity(double capacity) {
        this.capacity = capacity;
    }

    public void setFreespeed(double freespeed) {
        this.freespeed = freespeed;
    }

    public void setNumberOfLanes(double numberOfLanes) {
        this.numberOfLanes = numberOfLanes;
    }

    public void setAllowedModes(Set<String> allowedModes) {
        this.allowedModes = allowedModes == null || allowedModes.isEmpty()
                ? new LinkedHashSet<>(Set.of("car"))
                : new LinkedHashSet<>(allowedModes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Link that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
