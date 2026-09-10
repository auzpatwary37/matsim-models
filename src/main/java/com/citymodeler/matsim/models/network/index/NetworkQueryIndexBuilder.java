package com.citymodeler.matsim.models.network.index;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.network.OsmLaneHint;
import com.citymodeler.matsim.models.osm.network.OsmStopHint;
import java.util.List;
import java.util.Map;

public final class NetworkQueryIndexBuilder {

    private static final double DEFAULT_CELL_SIZE_METERS = 100.0;

    private NetworkQueryIndexBuilder() {
    }

    public static NetworkQueryIndex build(Network network, List<OsmStopHint> stopHints,
                                           Map<String, OsmLaneHint> laneHints) {
        LinkSpatialIndex linkIndex = new LinkSpatialIndex(network, DEFAULT_CELL_SIZE_METERS);
        NodeSpatialIndex nodeIndex = new NodeSpatialIndex(network, DEFAULT_CELL_SIZE_METERS);
        StopHintIndex stopIndex = new StopHintIndex(stopHints);
        LaneHintIndex laneIndex = new LaneHintIndex(laneHints);
        return new NetworkQueryIndex(network, linkIndex, nodeIndex, stopIndex, laneIndex);
    }

    public static NetworkQueryIndex build(Network network, List<OsmStopHint> stopHints,
                                           Map<String, OsmLaneHint> laneHints, double cellSizeMeters) {
        LinkSpatialIndex linkIndex = new LinkSpatialIndex(network, cellSizeMeters);
        NodeSpatialIndex nodeIndex = new NodeSpatialIndex(network, cellSizeMeters);
        StopHintIndex stopIndex = new StopHintIndex(stopHints);
        LaneHintIndex laneIndex = new LaneHintIndex(laneHints);
        return new NetworkQueryIndex(network, linkIndex, nodeIndex, stopIndex, laneIndex);
    }
}
