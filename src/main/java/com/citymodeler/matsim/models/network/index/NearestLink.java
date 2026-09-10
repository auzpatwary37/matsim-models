package com.citymodeler.matsim.models.network.index;

import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Coord;

public record NearestLink(
        String linkId,
        double distance,
        Coord closestPoint,
        double fractionAlongLink) {

    public NearestLink {
        Objects.requireNonNull(linkId, "linkId");
        Objects.requireNonNull(closestPoint, "closestPoint");
    }

    public static List<NearestLink> empty() {
        return List.of();
    }
}
