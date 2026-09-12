package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.transit.TransitRoute;

import java.util.List;

/**
 * Diagnostic report for a single mapped route.
 */
public record MappingReport(
        Id<TransitRoute> routeId,
        List<Id<com.citymodeler.matsim.models.network.Link>> networkRoute,
        List<String> artificialLinkIds,
        List<String> diagnostics) {

    public boolean hasArtificialLinks() {
        return !artificialLinkIds.isEmpty();
    }

    public int stopCount() {
        return networkRoute == null ? 0 : networkRoute.size();
    }
}
