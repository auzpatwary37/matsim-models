package com.citymodeler.matsim.models.osm.network;

import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmLaneResolver {

    public double resolve(OsmWayRecord way, OsmWayRule rule, boolean forward, boolean oneway) {
        OsmTagSafe tags = new OsmTagSafe(way.tags());

        String directionalKey = forward ? "lanes:forward" : "lanes:backward";
        Double directionalLanes = parseDouble(tags.get(directionalKey));

        if (oneway) {
            if (directionalLanes != null) {
                return directionalLanes;
            }
            Double total = parseDouble(tags.get("lanes"));
            if (total != null) {
                return total;
            }
            return rule.lanesPerDirection();
        }

        Double forwardLanes = parseDouble(tags.get("lanes:forward"));
        Double backwardLanes = parseDouble(tags.get("lanes:backward"));
        Double bothWays = parseDouble(tags.get("lanes:both_ways"));
        Double total = parseDouble(tags.get("lanes"));

        if (forwardLanes != null && backwardLanes != null) {
            double base = forward ? forwardLanes : backwardLanes;
            return base + (bothWays != null ? bothWays : 0.0);
        }

        if (total != null) {
            double perDirection = total / 2.0;
            return perDirection + (bothWays != null ? bothWays : 0.0);
        }

        return rule.lanesPerDirection() + (bothWays != null ? bothWays : 0.0);
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
