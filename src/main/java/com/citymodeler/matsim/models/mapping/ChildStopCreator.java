package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.transit.TransitStopFacility;
import com.citymodeler.matsim.models.transit.TransitStopArea;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates child stop facilities per (parent, link) combination.
 */
public final class ChildStopCreator {

    /**
     * Create a child stop ID from parent and link.
     * Convention: parentStopId.link:linkId
     * Collision: dots in parent are escaped by doubling.
     */
    public static String childStopId(String parentStopId, String linkId) {
        String escaped = parentStopId.replace(".", "..");
        return escaped + ".link:" + linkId;
    }

    /**
     * Create a child TransitStopFacility for the given parent and link.
     */
    public static TransitStopFacility createChild(TransitStopFacility parent,
                                                   Id<Link> linkId,
                                                   Coord coord) {
        String childId = childStopId(parent.getId().toString(), linkId.toString());
        TransitStopFacility child = new TransitStopFacility(
                Id.create(childId, TransitStopFacility.class),
                coord != null ? coord : parent.getCoord(),
                false);
        child.setName(parent.getName());
        child.setLinkId(linkId);
        child.setStopAreaId(Id.create(parent.getId().toString(), TransitStopArea.class));
        // Metadata
        child.getAttributes().putAttribute("gtfs:parentStopId", parent.getId().toString());
        Object feedId = parent.getAttributes().getAttribute("gtfs:feedId");
        if (feedId != null) {
            child.getAttributes().putAttribute("gtfs:feedId", feedId.toString());
        }
        Object agencyId = parent.getAttributes().getAttribute("gtfs:agencyId");
        if (agencyId != null) {
            child.getAttributes().putAttribute("gtfs:agencyId", agencyId.toString());
        }
        return child;
    }
}
