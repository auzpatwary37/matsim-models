package com.citymodeler.matsim.models.osm.network;

import java.util.List;

import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.osm.OsmImportIssue;

/** One link's lanes plus any issues raised while decomposing them. */
public record LaneDecomposition(LanesToLinkAssignment assignment, List<OsmImportIssue> issues) {
    public LaneDecomposition {
        issues = List.copyOf(issues);
    }
}
