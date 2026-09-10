package com.citymodeler.matsim.models.network.index;

import java.util.List;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Network;

/**
 * Composite query index combining spatial and hint indexes for the network.
 */
public final class NetworkQueryIndex {

    private final Network network;
    private final LinkSpatialIndex linkIndex;
    private final NodeSpatialIndex nodeIndex;
    private final StopHintIndex stopIndex;
    private final LaneHintIndex laneIndex;

    public NetworkQueryIndex(Network network, LinkSpatialIndex linkIndex,
                             NodeSpatialIndex nodeIndex, StopHintIndex stopIndex, LaneHintIndex laneIndex) {
        this.network = network;
        this.linkIndex = linkIndex;
        this.nodeIndex = nodeIndex;
        this.stopIndex = stopIndex;
        this.laneIndex = laneIndex;
    }

    public Network network() {
        return network;
    }

    public List<NearestLink> nearestLinks(Coord point, int maxResults, double maxDistanceMeters) {
        return linkIndex.nearestLinks(point, maxResults, maxDistanceMeters);
    }

    public List<NearestNode> nearestNodes(Coord point, int maxResults, double maxDistanceMeters) {
        return nodeIndex.nearestNodes(point, maxResults, maxDistanceMeters);
    }

    public List<String> stopHintsNear(Coord point, double maxDistanceMeters) {
        return stopIndex.stopsNear(point, maxDistanceMeters);
    }

    public List<String> laneHintsForLink(String linkId) {
        return laneIndex.hintsForLink(linkId);
    }
}
