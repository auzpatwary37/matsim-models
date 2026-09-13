package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;

import com.citymodeler.matsim.models.osm.OsmTagSet;

/**
 * Resolves the lane count that applies to one travel direction of an OSM way.
 * {@code lanes=*} is the total across BOTH directions; only an explicit directional tag or the
 * oneway assumption gives an unambiguous per-direction count. When a bidirectional way carries an
 * even total the OSM-documented even split is applied; an odd total is left undetermined (one
 * undivided lane + issue) rather than guessed.
 */
public final class OsmDirectionalLaneResolver {

    public OsmLaneCount resolve(OsmTagSet tags, boolean forward, boolean oneway) {
        List<String> issues = new ArrayList<>();
        String directionalKey = forward ? "lanes:forward" : "lanes:backward";
        Double directional = parse(tags.get(directionalKey), directionalKey, issues);
        Double total = parse(tags.get("lanes"), "lanes", issues);
        Double bothWays = parse(tags.get("lanes:both_ways"), "lanes:both_ways", issues);
        double both = bothWays != null ? bothWays : 0.0;

        if (oneway) {
            if (directional != null) {
                return new OsmLaneCount((int) Math.round(directional + both), LaneConfidence.PRESENT,
                        null, issues);
            }
            if (total != null) {
                double lanes = total + both;
                return new OsmLaneCount((int) Math.round(lanes), LaneConfidence.PRESENT, null, issues);
            }
            return new OsmLaneCount(1, LaneConfidence.ABSENT, null, issues);
        }

        Double fwd = parse(tags.get("lanes:forward"), "lanes:forward", issues);
        Double bwd = parse(tags.get("lanes:backward"), "lanes:backward", issues);
        if (fwd != null && bwd != null) {
            double lanes = (forward ? fwd : bwd) + both;
            return new OsmLaneCount((int) Math.round(lanes), LaneConfidence.PRESENT, null, issues);
        }

        if (total != null) {
            double directionalTotal = total - both;
            if (directionalTotal < 1.0) {
                issues.add("undetermined-lane-split");
                return new OsmLaneCount(1, LaneConfidence.UNDETERMINED_SPLIT, total, issues);
            }
            double perDirection = directionalTotal / 2.0;
            if (Math.abs(perDirection - Math.rint(perDirection)) > 1e-9) {
                issues.add("undetermined-lane-split");
                return new OsmLaneCount(1, LaneConfidence.UNDETERMINED_SPLIT, directionalTotal, issues);
            }
            int lanes = (int) Math.round(perDirection + both);
            return new OsmLaneCount(lanes, LaneConfidence.EVEN_SPLIT, null, issues);
        }

        return new OsmLaneCount(1, LaneConfidence.ABSENT, null, issues);
    }

    private static Double parse(String value, String key, List<String> issues) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double d = Double.parseDouble(value.trim());
            if (d < 1.0 || Math.abs(d - Math.rint(d)) > 1e-9) {
                issues.add("malformed-lane-count");
                return null;
            }
            return d;
        } catch (NumberFormatException e) {
            issues.add("malformed-lane-count");
            return null;
        }
    }
}
