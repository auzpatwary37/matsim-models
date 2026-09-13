package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.osm.OsmImportIssue;

/** Lanes for a whole network plus decomposition issues. */
public record LaneDefinitionResult(Lanes lanes, List<OsmImportIssue> issues) {
    public LaneDefinitionResult {
        lanes = Objects.requireNonNull(lanes, "lanes");
        issues = List.copyOf(issues);
    }
}
