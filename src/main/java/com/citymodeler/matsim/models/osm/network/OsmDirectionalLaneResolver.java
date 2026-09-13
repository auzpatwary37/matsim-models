package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;

import com.citymodeler.matsim.models.osm.OsmTagSet;

/**
 * Resolves the lane count that applies to one travel direction of an OSM way.
 *
 * <p>{@code lanes=*} is the total across BOTH directions; only an explicit directional tag or the
 * oneway assumption gives an unambiguous per-direction count. When a declared {@code lanes=*}
 * exists the WHOLE tag set is validated first (spec §1a step 1): explicit directional tags are
 * authoritative only within an internally consistent set and are never permission to exceed the
 * declared physical total. On inconsistency the contradictory directional values are not used as
 * physical counts; resolution falls back to the total-derived split/undetermined path. A
 * bidirectional way with an even directional total uses the OSM-documented even split; an odd total
 * is left undetermined and produces {@code round(T / 2)} physical lane objects rather than a
 * fabricated per-direction count.</p>
 *
 * <p>{@code lanes:both_ways} is preserved as provenance only (spec §1a step 5): it is never added to
 * a direction's count, so the directed lane objects sum to the declared physical total.</p>
 *
 * <p>Undetermined-split capacity caveat (spec §1e): for an odd bidirectional total each direction
 * emits {@code round(T / 2)} lane objects, so the pair may be one lane object more than the physical
 * total. This is a deliberate directional conservative representation flagged
 * {@link LaneConfidence#UNDETERMINED_SPLIT}, not a strict physical-capacity decomposition; consumers
 * that sum lane capacity across both directions must respect the confidence flag.</p>
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

        Double directional = forward ? forwardCount : backwardCount;
        Double opposite = forward ? backwardCount : forwardCount;

        if (oneway) {
            // A single travelled direction: a directional tag wins, else the literal total (the
            // wiki's one-way assumption), else the rule default. There is no cross-direction check.
            if (directional != null) {
                return laneCount(Math.round(directional), LaneConfidence.PRESENT, null, bothWays, issues);
            }
            if (total != null) {
                return laneCount(Math.round(total), LaneConfidence.PRESENT, null, bothWays, issues);
            }
            return laneCount(Math.round(defaultLanesPerDirection), LaneConfidence.ABSENT, null,
                    bothWays, issues);
        }

        // Spec §1a step 1: with a declared physical total, validate the complete tag set BEFORE
        // trusting any explicit directional value. An inconsistent set must not yield a physical
        // count that exceeds (or contradicts) lanes=*.
        if (total != null && !tagSetIsConsistent(total, forwardCount, backwardCount, bothWays)) {
            issues.add("inconsistent-lane-tags");
            // Preserve the contradictory directional values as provenance only; fall back to the
            // total-derived split / undetermined handling (spec §1a step 4).
            return totalDerivedSplit(total, bothWays, issues);
        }

        // No declared total to violate: a lone or pair of directional tags is authoritative as-is.
        if (total == null) {
            if (directional != null) {
                return laneCount(Math.round(directional), LaneConfidence.PRESENT, null, bothWays, issues);
            }
            return laneCount(Math.round(defaultLanesPerDirection), LaneConfidence.ABSENT, null,
                    bothWays, issues);
        }

        // lanes=* present and consistent, and no directional tag for the requested direction.
        if (directional == null) {
            if (opposite != null) {
                // A lone tag for the OTHER direction: derive this direction from the declared total
                // (the consistency check above guaranteed it derives to >= 1).
                double other = total - opposite - (bothWays != null ? bothWays : 0.0);
                return laneCount(Math.round(other), LaneConfidence.PRESENT, null, bothWays, issues);
            }
            // Neither directional tag present: the total-derived split / undetermined handling.
            return totalDerivedSplit(total, bothWays, issues);
        }

        // An explicit directional tag (lone or paired) that passed the consistency check is
        // authoritative within that consistent set.
        return laneCount(Math.round(directional), LaneConfidence.PRESENT, null, bothWays, issues);
    }

    /**
     * Spec §1a step 1: the complete tag set is inconsistent when a declared {@code lanes=*} is
     * contradicted by the directional tags. Call only when {@code total} is non-null.
     */
    private static boolean tagSetIsConsistent(double total, Double forwardCount,
                                              Double backwardCount, Double bothWays) {
        double both = bothWays != null ? bothWays : 0.0;
        if (forwardCount != null && backwardCount != null) {
            // Both directions declared: their sum plus the shared lanes must equal the total.
            return Math.abs(forwardCount + backwardCount + both - total) < 1e-9;
        }
        // A single directional tag: it (plus the shared lanes) must fit within the total and leave
        // at least one lane for the missing direction.
        Double known = forwardCount != null ? forwardCount : backwardCount;
        if (known == null) {
            return true;
        }
        return known + both <= total && total - known - both >= 1.0;
    }

    /** Total-derived split / undetermined handling (spec §1a step 4). */
    private static OsmLaneCount totalDerivedSplit(double total, Double bothWays, List<String> issues) {
        double both = bothWays != null ? bothWays : 0.0;
        double directionalTotal = total - both;
        if (directionalTotal >= 1.0
                && Math.abs(directionalTotal / 2.0 - Math.rint(directionalTotal / 2.0)) < 1e-9) {
            return laneCount(Math.round(directionalTotal / 2.0), LaneConfidence.EVEN_SPLIT, null,
                    bothWays, issues);
        }
        // Spec §1e: round(T / 2) per direction may exceed the physical total by one lane object;
        // this is a flagged directional representation, not a physical decomposition.
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
