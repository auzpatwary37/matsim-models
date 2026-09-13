package com.citymodeler.matsim.models.mapping;

/**
 * Weight vector for stop-candidate scoring factors. Covers exactly the factors the scorer computes:
 * distance, mode compatibility, and name similarity between the GTFS stop name and a candidate
 * link's source road name(s) (`osm:name`, `osm:sourceNames`).
 */
public record CandidateScoreWeights(
        double distance,
        double modeCompatibility,
        double nameSimilarity) {

    public static CandidateScoreWeights defaults() {
        return new CandidateScoreWeights(1.0, 0.8, 0.5);
    }

    public void validate() {
        if (distance < 0 || modeCompatibility < 0 || nameSimilarity < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        if (distance + modeCompatibility + nameSimilarity == 0) {
            throw new IllegalArgumentException("Not all positive weights may be zero");
        }
    }
}
