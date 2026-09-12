package com.citymodeler.matsim.models.mapping;

/**
 * A scored candidate link for a GTFS stop.
 */
public record StopCandidate(
        com.citymodeler.matsim.models.api.Id<com.citymodeler.matsim.models.network.Link> linkId,
        double distanceMeters,
        double score,
        boolean hasStopHint,
        boolean hasLaneHint,
        boolean isArtificial) {

    public static StopCandidate artificial(
            com.citymodeler.matsim.models.api.Id<com.citymodeler.matsim.models.network.Link> linkId,
            double distanceMeters) {
        return new StopCandidate(linkId, distanceMeters, 0.0, false, false, true);
    }
}
