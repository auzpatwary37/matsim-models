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
        // Parse each distinct tag exactly once so a malformed tag yields a single issue.
        Double total = parse(tags.get("lanes"), issues);
        Double forwardCount = parse(tags.get("lanes:forward"), issues);
        Double backwardCount = parse(tags.get("lanes:backward"), issues);
        Double bothWays = parse(tags.get("lanes:both_ways"), issues);
        double both = bothWays != null ? bothWays : 0.0;

        Double directional = forward ? forwardCount : backwardCount;

        if (oneway) {
            if (directional != null) {
                return laneCount(Math.round(directional + both), LaneConfidence.PRESENT, null, issues);
            }
            if (total != null) {
                return laneCount(Math.round(total + both), LaneConfidence.PRESENT, null, issues);
            }
            return laneCount(1, LaneConfidence.ABSENT, null, issues);
        }

        // A single explicit directional tag is authoritative for its own direction.
        if (directional != null) {
            return laneCount(Math.round(directional + both), LaneConfidence.PRESENT, null, issues);
        }

        // No explicit tag for the requested direction: derive it from the total.
        if (total != null) {
            double directionalTotal = total - both;
            if (directionalTotal < 1.0) {
                issues.add("undetermined-lane-split");
                return laneCount(1, LaneConfidence.UNDETERMINED_SPLIT, directionalTotal, issues);
            }
            double perDirection = directionalTotal / 2.0;
            if (Math.abs(perDirection - Math.rint(perDirection)) > 1e-9) {
                issues.add("undetermined-lane-split");
                return laneCount(1, LaneConfidence.UNDETERMINED_SPLIT, directionalTotal, issues);
            }
            return laneCount(Math.round(perDirection + both), LaneConfidence.EVEN_SPLIT, null, issues);
        }

        return laneCount(1, LaneConfidence.ABSENT, null, issues);
    }

    private static OsmLaneCount laneCount(long lanes, String confidence, Double undeterminedTotal,
            List<String> issues) {
        return new OsmLaneCount((int) lanes, confidence, undeterminedTotal, issues);
    }

    private static Double parse(String value, List<String> issues) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            issues.add("malformed-lane-count");
            return null;
        }
        try {
            double d = Double.parseDouble(value.trim());
            if (!Double.isFinite(d) || d < 1.0 || Math.abs(d - Math.rint(d)) > 1e-9) {
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
