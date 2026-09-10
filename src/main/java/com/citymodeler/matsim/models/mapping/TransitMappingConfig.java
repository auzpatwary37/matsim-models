package com.citymodeler.matsim.models.mapping;

import java.util.Map;

/**
 * Configuration for transit-to-network mapping.
 */
public record TransitMappingConfig(
        double maxLinkCandidateDistanceMeters,
        double candidateDistanceMultiplier,
        int nLinkThreshold,
        double routingWithCandidateDistance,
        CandidateScoreWeights weights,
        Map<String, CandidateScoreWeights> perModeOverrides) {

    public static TransitMappingConfig defaults() {
        return new TransitMappingConfig(
                500.0, 2.0, 20, 1000.0,
                CandidateScoreWeights.defaults(), Map.of());
    }

    public CandidateScoreWeights weightsForMode(String mode) {
        return perModeOverrides.getOrDefault(mode, weights);
    }

    public void validate() {
        weights.validate();
        for (var w : perModeOverrides.values()) {
            w.validate();
        }
        if (maxLinkCandidateDistanceMeters < 0) {
            throw new IllegalArgumentException("maxLinkCandidateDistanceMeters must be >= 0");
        }
    }
}
