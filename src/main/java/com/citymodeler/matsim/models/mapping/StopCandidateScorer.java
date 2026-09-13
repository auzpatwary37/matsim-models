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

            double score = computeScore(nl.distance(), link, w, mode, stop.getName());
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
        return modeCompatible(link.getAllowedModes(), mode);
    }

    /**
     * Shared mode-compatibility policy for stop candidates AND path traversal. A link with no
     * declared modes carries anything; otherwise the requested mode must be allowed, a generic
     * {@code pt} request requires a transit-capable link (not a car-only street), and a specific
     * requested mode is satisfied by an exact match or a link-level {@code pt} super-mode.
     */
    static boolean modeCompatible(Set<String> allowed, String mode) {
        if (allowed == null || allowed.isEmpty()) {
            return true; // unrestricted link carries anything
        }
        if (allowed.contains(mode)) {
            return true;
        }
        if ("pt".equals(mode)) {
            for (String m : allowed) {
                if (TRANSIT_MODES.contains(m)) {
                    return true;
                }
            }
            return false;
        }
        for (String m : allowed) {
            if ("pt".equals(m) && TRANSIT_MODES.contains(mode)) {
                return true;
            }
        }
        return false;
    }

    private double computeScore(double distance, Link link, CandidateScoreWeights w, String mode,
                                String stopName) {
        double score = 0;
        // Primary: inverse distance
        score -= w.distance() * distance / 100.0;
        // Mode compatibility bonus: exact mode match beats generic
        Set<String> modes = link.getAllowedModes();
        if (modes != null && modes.contains(mode)) {
            score += w.modeCompatibility();
        }
        // Name similarity: a candidate whose source road name matches the stop name is preferred,
        // which disambiguates among several nearby links (e.g. both sides of a dual carriageway).
        if (w.nameSimilarity() > 0) {
            score += w.nameSimilarity() * nameSimilarity(stopName, link);
        }
        return score;
    }

    /** Normalized name similarity in [0,1] between the stop name and the link's source road name(s). */
    static double nameSimilarity(String stopName, Link link) {
        if (stopName == null || stopName.isBlank()) {
            return 0.0;
        }
        String stop = normalizeName(stopName);
        if (stop.isEmpty()) {
            return 0.0;
        }
        double best = 0.0;
        for (String candidate : linkNames(link)) {
            String name = normalizeName(candidate);
            if (name.isEmpty()) {
                continue;
            }
            if (name.equals(stop)) {
                return 1.0;
            }
            if (name.contains(stop) || stop.contains(name)) {
                best = Math.max(best, 0.75);
            } else {
                best = Math.max(best, tokenOverlap(stop, name));
            }
        }
        return best;
    }

    private static List<String> linkNames(Link link) {
        List<String> names = new ArrayList<>();
        addNames(link.getAttributes().getAttribute("osm:name"), names);
        addNames(link.getAttributes().getAttribute("osm:sourceNames"), names);
        return names;
    }

    private static void addNames(Object value, List<String> out) {
        if (value == null) {
            return;
        }
        for (String part : value.toString().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
    }

    private static String normalizeName(String value) {
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    /** Jaccard-ish token overlap so "Rue de Hamm" matches "Hamm" and shares partial words. */
    private static double tokenOverlap(String a, String b) {
        Set<String> ta = new java.util.TreeSet<>(List.of(a.split(" ")));
        Set<String> tb = new java.util.TreeSet<>(List.of(b.split(" ")));
        ta.removeIf(t -> t.length() < 3);
        tb.removeIf(t -> t.length() < 3);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0;
        }
        Set<String> shared = new java.util.TreeSet<>(ta);
        shared.retainAll(tb);
        return shared.isEmpty() ? 0.0 : (double) shared.size() / Math.max(ta.size(), tb.size());
    }
}
