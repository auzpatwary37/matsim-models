package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.transit.TransitSchedule;

import java.util.List;
import java.util.Map;

/**
 * Result of mapping a transit schedule onto a network.
 */
public record TransitMappingResult(
        TransitSchedule mappedSchedule,
        Network mappedNetwork,
        List<MappingReport> reports,
        List<String> warnings) {

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
