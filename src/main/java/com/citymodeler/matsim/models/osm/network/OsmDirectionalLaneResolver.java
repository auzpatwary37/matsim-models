package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;

import com.citymodeler.matsim.models.osm.OsmTagSet;

/**
 * Resolves the lane count that applies to one travel direction of an OSM way.
 *
 * <p>{@code lanes=*} is the total across BOTH directions; only an explicit directional tag or the
 * oneway assumption gives an unambiguous per-direction count. A single directional tag with a total
 * derives the missing direction as {@code total - known - lanes:both_ways} (spec §1a step 3); when
 * that is inconsistent the tags are flagged and the total split/undetermined handling applies. A
 * bidirectional way with an even directional total uses the OSM-documented even split; an odd total
 * is left undetermined and produces {@code round(T / 2)} physical lane objects rather than a
 * fabricated per-direction count.</p>
 *
 * <p>{@code lanes:both_ways} is preserved as provenance only (spec §1a step 4): it is never added to
 * a direction's count, so the directed lane objects sum to the declared physical total.</p>
 */
public final class OsmDirectionalLaneResolver {

    public OsmLaneCount resolve(OsmTagSet tags, boolean forward, boolean oneway,
                                double defaultLanesPerDirection) {
        List<String> issues = new ArrayList<>();
        // Parse each distinct tag exactly once so a malformed tag yields a single issue.
        Double total = parse(tags.get("lanes"), issues);
        Double forwardCount = parse(tags.get("lanes:forward"), issues);
        Double backwardCount = parse(tags.get("lanes:backward"), issues);
        Double bothWays = parse(tags.get("lanes:both_ways"), issues);
        double both = bothWays != null ? bothWays : 0.0;

        Double directional = forward ? forwardCount : backwardCount;
        Double opposite = forward ? backwardCount : forwardCount;

        if (oneway) {
            if (directional != null) {
                return laneCount(Math.round(directional), LaneConfidence.PRESENT, null, bothWays, issues);
            }
            if (total != null) {
                return laneCount(Math.round(total), LaneConfidence.PRESENT, null, bothWays, issues);
            }
            return laneCount(Math.round(defaultLanesPerDirection), LaneConfidence.ABSENT, null,
                    bothWays, issues);
        }

        // An explicit tag for the requested direction is authoritative for that direction.
        if (directional != null) {
            return laneCount(Math.round(directional), LaneConfidence.PRESENT, null, bothWays, issues);
        }

        if (total == null) {
            return laneCount(Math.round(defaultLanesPerDirection), LaneConfidence.ABSENT, null,
                    bothWays, issues);
        }

        // A single directional tag with a total: derive the missing direction from the declared
        // physical total. If that leaves no lane for this direction the tags are inconsistent.
        if (opposite != null) {
            double other = total - opposite - both;
            if (other >= 1.0) {
                return laneCount(Math.round(other), LaneConfidence.PRESENT, null, bothWays, issues);
            }
            issues.add("inconsistent-lane-tags");
            // Fall through to the total-split / undetermined handling below.
        }

        double directionalTotal = total - both;
        if (directionalTotal >= 1.0
                && Math.abs(directionalTotal / 2.0 - Math.rint(directionalTotal / 2.0)) < 1e-9) {
            return laneCount(Math.round(directionalTotal / 2.0), LaneConfidence.EVEN_SPLIT, null,
                    bothWays, issues);
        }
        issues.add("undetermined-lane-split");
        int lanes = (int) Math.max(1, Math.round(directionalTotal / 2.0));
        return new OsmLaneCount(lanes, LaneConfidence.UNDETERMINED_SPLIT, directionalTotal, bothWays,
                issues);
    }

    private static OsmLaneCount laneCount(long lanes, String confidence, Double undeterminedTotal,
            Double bothWays, List<String> issues) {
        return new OsmLaneCount((int) lanes, confidence, undeterminedTotal, bothWays, issues);
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
