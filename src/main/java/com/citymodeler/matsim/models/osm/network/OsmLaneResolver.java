package com.citymodeler.matsim.models.osm.network;

import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmLaneResolver {

    public double resolve(OsmWayRecord way, OsmWayRule rule, boolean forward, boolean oneway) {
        OsmTagSafe tags = new OsmTagSafe(way.tags());

        String directionalKey = forward ? "lanes:forward" : "lanes:backward";
        Double directionalLanes = parseDouble(tags.get(directionalKey));
        Double total = parseDouble(tags.get("lanes"));
        Double bothWays = parseDouble(tags.get("lanes:both_ways"));

        if (oneway) {
            if (directionalLanes != null) {
                return directionalLanes;
            }
            if (total != null) {
                return total;
            }
            return rule.lanesPerDirection();
        }

        Double forwardLanes = parseDouble(tags.get("lanes:forward"));
        Double backwardLanes = parseDouble(tags.get("lanes:backward"));

        if (forwardLanes != null && backwardLanes != null) {
            return forward ? forwardLanes : backwardLanes;
        }

        if (total != null) {
            double effectiveTotal = bothWays != null ? total - bothWays : total;
            return effectiveTotal / 2.0;
        }

        return rule.lanesPerDirection();
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double d = Double.parseDouble(value.trim());
            return d > 0 ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class OsmTagSafe {
        private final com.citymodeler.matsim.models.osm.OsmTagSet tags;

        OsmTagSafe(com.citymodeler.matsim.models.osm.OsmTagSet tags) {
            this.tags = tags;
        }

        String get(String key) {
            return tags.get(key);
        }
    }
}
