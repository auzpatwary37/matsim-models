package com.citymodeler.matsim.models.network.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.citymodeler.matsim.models.osm.network.OsmLaneHint;

/**
 * Index of lane hints by link ID for fast lookup.
 */
public final class LaneHintIndex {

    private final Map<String, OsmLaneHint> byLinkId;

    public LaneHintIndex(Map<String, OsmLaneHint> hints) {
        this.byLinkId = new TreeMap<>(hints);
    }

    public OsmLaneHint hintForLink(String linkId) {
        return byLinkId.get(linkId);
    }

    public List<String> hintsForLink(String linkId) {
        OsmLaneHint hint = byLinkId.get(linkId);
        return hint != null ? List.of(hint.osmWayId()) : List.of();
    }

    public Map<String, OsmLaneHint> asMap() {
        return Collections.unmodifiableMap(byLinkId);
    }

    public int size() {
        return byLinkId.size();
    }
}
