package com.citymodeler.matsim.models.mapping;

/**
 * Weight vector for stop-candidate scoring factors.
 *
 * <p>Review #12: this record previously advertised stop-hint proximity, name similarity, platform
 * hint, lane hint, inaccessibility penalty and turn penalty, but the scorer only ever applied
 * distance and mode compatibility. Silently-ignored, configurable weights are misleading and make
 * tuning ineffective, so the vector is narrowed to the factors that are actually computed. The
 * unimplemented factors are deliberately NOT part of the public tuning surface until they are wired
 * up against the Phase-1 indexes.
 */
public record CandidateScoreWeights(
        double distance,
        double modeCompatibility) {

    public static CandidateScoreWeights defaults() {
        return new CandidateScoreWeights(1.0, 0.8);
    }

    public void validate() {
        if (distance < 0 || modeCompatibility < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        if (distance + modeCompatibility == 0) {
            throw new IllegalArgumentException("Not all positive weights may be zero");
        }
    }
}
