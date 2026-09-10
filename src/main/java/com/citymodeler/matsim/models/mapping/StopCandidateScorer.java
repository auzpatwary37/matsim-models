package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.network.index.NearestLink;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores candidate links near a GTFS stop using Phase 1 spatial indexes.
 */
public final class StopCandidateScorer {

    private final TransitMappingConfig config;
    private final LinkSpatialIndex spatialIndex;
    private final Network network;

    public StopCandidateScorer(TransitMappingConfig config, LinkSpatialIndex spatialIndex, Network network) {
        this.config = config;
        this.spatialIndex = spatialIndex;
        this.network = network;
    }

    public List<StopCandidate> score(TransitStopFacility stop, String mode) {
        double maxDist = config.maxLinkCandidateDistanceMeters();
        if (maxDist == 0) return List.of();

        CandidateScoreWeights w = config.weightsForMode(mode);
        int n = (int) (config.nLinkThreshold() * config.candidateDistanceMultiplier());
        List<NearestLink> nearby = spatialIndex.nearestLinks(stop.getCoord(), n, maxDist);

        List<StopCandidate> candidates = new ArrayList<>();
        for (NearestLink nl : nearby) {
            Id<Link> linkId = Id.create(nl.linkId(), Link.class);
            Link link = network.getLinks().get(linkId);
            if (link == null) continue;

            double score = computeScore(nl.distance(), link, w);
            candidates.add(new StopCandidate(linkId, nl.distance(), score, false, false, false));
        }
        return candidates;
    }

    private double computeScore(double distance, Link link, CandidateScoreWeights w) {
        double score = 0;
        score -= w.distance() * distance / 100.0;
        Object modes = link.getAttributes().getAttribute("transportModes");
        if (modes != null) {
            score += w.modeCompatibility();
        }
        return score;
    }
}
