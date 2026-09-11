package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.network.index.NearestLink;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Scores candidate links near a GTFS stop using Phase 1 spatial indexes.
 * Enforces mode compatibility: links that cannot carry the requested mode are rejected.
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
        int n = (int) (config.nLinkThreshold() * config.candidateDistanceMultiplier() * 2);
        List<NearestLink> nearby = spatialIndex.nearestLinks(stop.getCoord(), n, maxDist);

        List<StopCandidate> candidates = new ArrayList<>();
        for (NearestLink nl : nearby) {
            Id<Link> linkId = Id.create(nl.linkId(), Link.class);
            Link link = network.getLinks().get(linkId);
            if (link == null) continue;

            // Mode compatibility: reject links that cannot carry the requested mode
            if (!isModeCompatible(link, mode)) continue;

            double score = computeScore(nl.distance(), link, w, mode);
            candidates.add(new StopCandidate(linkId, nl.distance(), score, false, false, false));
        }
        candidates.sort(Comparator.comparingDouble(StopCandidate::score).reversed());
        return candidates;
    }

    /**
     * Network modes that are transit-capable. Review #7: a generic {@code pt} request must NOT be
     * accepted unconditionally (the old code returned true for any link). A {@code pt} route is only
     * compatible with a link that can actually carry transit — a car-only street is rejected so the
     * scorer falls through to a transit-capable (or, failing that, artificial) candidate.
     */
    static final Set<String> TRANSIT_MODES = Set.of(
            "bus", "tram", "rail", "light_rail", "subway", "metro", "trolleybus",
            "cable_car", "aerial_lift", "funicular", "gondola", "monorail", "ferry",
            "taxi", "pt");

    private boolean isModeCompatible(Link link, String mode) {
        Set<String> allowed = link.getAllowedModes();
        if (allowed == null || allowed.isEmpty()) return true; // unrestricted link carries anything
        if (allowed.contains(mode)) return true;
        // Generic transit: require the link to be transit-capable, not a car-only street.
        if ("pt".equals(mode)) {
            for (String m : allowed) {
                if (TRANSIT_MODES.contains(m)) return true;
            }
            return false;
        }
        // A specific requested mode: "pt" on the link is a transit super-mode.
        for (String m : allowed) {
            if (isSubMode(mode, m)) return true;
        }
        return false;
    }

    private boolean isSubMode(String requested, String allowed) {
        if (requested.equals(allowed)) return true;
        if ("pt".equals(allowed) && TRANSIT_MODES.contains(requested)) return true;
        return false;
    }

    private double computeScore(double distance, Link link, CandidateScoreWeights w, String mode) {
        double score = 0;
        // Primary: inverse distance
        score -= w.distance() * distance / 100.0;
        // Mode compatibility bonus: exact mode match beats generic
        Set<String> modes = link.getAllowedModes();
        if (modes != null && modes.contains(mode)) {
            score += w.modeCompatibility();
        }
        return score;
    }
}
