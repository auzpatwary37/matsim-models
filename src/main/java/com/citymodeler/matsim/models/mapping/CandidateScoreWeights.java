package com.citymodeler.matsim.models.mapping;

import java.util.Objects;

/**
 * Weight vector for stop-candidate scoring factors.
 */
public record CandidateScoreWeights(
        double distance,
        double modeCompatibility,
        double stopHintProximity,
        double nameSimilarity,
        double platformHint,
        double laneHint,
        double inaccessibilityPenalty,
        double turnPenalty) {

    public static CandidateScoreWeights defaults() {
        return new CandidateScoreWeights(1.0, 0.8, 2.0, 0.5, 0.6, 0.4, 1.5, 0.7);
    }

    public void validate() {
        if (distance < 0 || modeCompatibility < 0 || stopHintProximity < 0
                || nameSimilarity < 0 || platformHint < 0 || laneHint < 0
                || inaccessibilityPenalty < 0 || turnPenalty < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        if (distance + modeCompatibility + stopHintProximity + nameSimilarity
                + platformHint + laneHint == 0) {
            throw new IllegalArgumentException("Not all positive weights may be zero");
        }
    }
}
