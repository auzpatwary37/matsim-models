package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Coord;

public record OsmPolyline(List<Coord> points) {

    public OsmPolyline {
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (points.size() < 2) {
            throw new IllegalArgumentException("Polyline requires at least two points");
        }
    }

    public double length() {
        double total = 0;
        for (int i = 0; i + 1 < points.size(); i++) {
            double dx = points.get(i).getX() - points.get(i + 1).getX();
            double dy = points.get(i).getY() - points.get(i + 1).getY();
            total += Math.sqrt(dx * dx + dy * dy);
        }
        return total;
    }
}
