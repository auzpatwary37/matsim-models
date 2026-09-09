package com.citymodeler.matsim.models.osm.network;

import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmSpeedResolver {

    private static final double MAXSPEED_WALK_KPH = 5.0;

    public double resolve(OsmWayRecord way, OsmWayRule rule, boolean forward) {
        String directionalKey = forward ? "maxspeed:forward" : "maxspeed:backward";
        String value = way.tags().get(directionalKey);
        if (value == null || value.isBlank()) {
            value = way.tags().get("maxspeed");
        }
        if (value == null || value.isBlank()) {
            return rule.freespeedMetersPerSecond();
        }
        Double speed = parseSpeed(value);
        return speed != null ? speed : rule.freespeedMetersPerSecond();
    }

    private static Double parseSpeed(String value) {
        String trimmed = value.trim();
        if ("none".equals(trimmed)) {
            return null;
        }
        if ("walk".equals(trimmed)) {
            return MAXSPEED_WALK_KPH / 3.6;
        }
        if (trimmed.endsWith(" mph")) {
            try {
                double mph = Double.parseDouble(trimmed.substring(0, trimmed.length() - 4).trim());
                return mph * 1.609344 / 3.6;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (trimmed.endsWith("km/h")) {
            try {
                double kph = Double.parseDouble(trimmed.substring(0, trimmed.length() - 4).trim());
                return kph / 3.6;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            double kph = Double.parseDouble(trimmed);
            if (kph <= 0 || kph > 300) {
                return null;
            }
            return kph / 3.6;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
