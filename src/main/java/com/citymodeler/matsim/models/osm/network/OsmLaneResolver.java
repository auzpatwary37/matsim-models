package com.citymodeler.matsim.models.osm.network;

import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Resolves the per-direction lane count used by the base network ({@code network.xml} {@code permlanes}
 * and capacity). It delegates to {@link OsmDirectionalLaneResolver} so the base network and
 * {@code laneDefinitions.xml} share ONE validated lane-count hierarchy (spec §1a) and cannot diverge:
 *
 * <ul>
 *   <li>explicit directional tags are authoritative only within a tag set consistent with the declared
 *       {@code lanes=*}; a contradictory set falls back to the total-derived split;</li>
 *   <li>{@code lanes:both_ways} is never duplicated across directions;</li>
 *   <li>a one-way way validates a directional tag against {@code lanes=*} too;</li>
 *   <li>absent lane tags fall back to the rule's {@code lanesPerDirection()}.</li>
 * </ul>
 *
 * <p>The returned count is integral ({@link OsmDirectionalLaneResolver} rounds an odd bidirectional
 * total to {@code round(T / 2)}), so the emitted link never carries a fractional {@code permlanes}.
 * The result is directional: for a bidirectional way the two directions can differ (e.g.
 * {@code lanes:forward=1} / {@code lanes:backward=2}).</p>
 */
public final class OsmLaneResolver {

    private final OsmDirectionalLaneResolver delegate = new OsmDirectionalLaneResolver();

    public double resolve(OsmWayRecord way, OsmWayRule rule, boolean forward, boolean oneway) {
        return delegate.resolve(way.tags(), forward, oneway, rule.lanesPerDirection()).lanes();
    }
}
