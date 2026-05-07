package com.citymodeler.matsim.models.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;

public final class Node {
    private final Id<Node> id;
    private Coord coord;
    private final Map<Id<Link>, Link> inLinks = new LinkedHashMap<>();
    private final Map<Id<Link>, Link> outLinks = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public Node(Id<Node> id, Coord coord) {
        this.id = Objects.requireNonNull(id, "id");
        this.coord = Objects.requireNonNull(coord, "coord");
    }

    public Id<Node> getId() {
        return id;
    }

    public Coord getCoord() {
        return coord;
    }

    public void setCoord(Coord coord) {
        this.coord = Objects.requireNonNull(coord, "coord");
    }

    public Map<Id<Link>, Link> getInLinks() {
        return inLinks;
    }

    public Map<Id<Link>, Link> getOutLinks() {
        return outLinks;
    }

    public Attributes getAttributes() {
        return attributes;
    }
}
