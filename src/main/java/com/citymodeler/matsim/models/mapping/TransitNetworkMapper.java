package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.network.turnrestrictions.TurnRestrictionIndex;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a transit schedule onto a road network.
 *
 * <p>For each transit route, assigns links to stops and builds the network route.
 * Creates artificial links where stops cannot be connected through existing roads.</p>
 */
public final class TransitNetworkMapper {

    private final TransitMappingConfig config;
    private final LinkSpatialIndex spatialIndex;
    private final TurnRestrictionIndex turnRestrictionIndex;

    public TransitNetworkMapper(TransitMappingConfig config,
                                LinkSpatialIndex spatialIndex,
                                TurnRestrictionIndex turnRestrictionIndex) {
        this.config = config;
        this.spatialIndex = spatialIndex;
        this.turnRestrictionIndex = turnRestrictionIndex;
        config.validate();
    }

    public TransitMappingResult map(TransitSchedule schedule, Network network) {
        List<String> warnings = new ArrayList<>();
        List<MappingReport> reports = new ArrayList<>();
        StopCandidateScorer scorer = new StopCandidateScorer(config, spatialIndex, network);
        ArtificialLinkFactory linkFactory = new ArtificialLinkFactory(network);

        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                MappingReport report = mapRoute(schedule, network, route, scorer, linkFactory, warnings);
                reports.add(report);
            }
        }

        schedule.postProcess();
        return new TransitMappingResult(schedule, network, reports, warnings);
    }

    private MappingReport mapRoute(TransitSchedule schedule, Network network,
                                    TransitRoute route, StopCandidateScorer scorer,
                                    ArtificialLinkFactory factory, List<String> warnings) {
        String mode = route.getTransportMode() != null ? route.getTransportMode() : "pt";
        List<TransitRouteStop> stops = route.getStops();
        List<Id<Link>> networkRoute = new ArrayList<>();
        List<String> artificialIds = new ArrayList<>();

        if (stops.isEmpty()) {
            return new MappingReport(route.getId(), List.of(), List.of(), List.of("empty route"));
        }

        // Assign link to each stop
        Map<Id<TransitStopFacility>, Id<Link>> stopLinks = new LinkedHashMap<>();
        for (TransitRouteStop stop : stops) {
            TransitStopFacility facility = schedule.getFacilities().get(stop.getStopFacilityId());
            if (facility == null) continue;

            // If facility already has a link assigned (from previous mapping), reuse it
            if (facility.getLinkId() != null) {
                stopLinks.put(stop.getStopFacilityId(), facility.getLinkId());
                continue;
            }

            List<StopCandidate> candidates = scorer.score(facility, mode);
            if (candidates.isEmpty()) {
                // Create artificial loop
                String ctx = facility.getId().toString() + "_" + route.getId().toString();
                Link loop = factory.createLoop(facility.getCoord(), mode, ctx);
                facility.setLinkId(loop.getId());
                stopLinks.put(stop.getStopFacilityId(), loop.getId());
                artificialIds.add(loop.getId().toString());
                warnings.add("No candidate links for stop " + facility.getId() + ", created loop");
            } else {
                StopCandidate best = candidates.get(0);
                facility.setLinkId(best.linkId());
                stopLinks.put(stop.getStopFacilityId(), best.linkId());
            }
        }

        // Build network route by collecting stop links in order
        for (TransitRouteStop stop : stops) {
            Id<Link> linkId = stopLinks.get(stop.getStopFacilityId());
            if (linkId != null) {
                if (networkRoute.isEmpty() || !networkRoute.get(networkRoute.size() - 1).equals(linkId)) {
                    networkRoute.add(linkId);
                }
            }
        }

        if (!networkRoute.isEmpty()) {
            route.setNetworkRoute(networkRoute);
        }

        // Create child stop facilities
        for (TransitRouteStop stop : stops) {
            TransitStopFacility facility = schedule.getFacilities().get(stop.getStopFacilityId());
            if (facility == null || facility.getLinkId() == null) continue;
            String childId = ChildStopCreator.childStopId(facility.getId().toString(),
                    facility.getLinkId().toString());
            if (!schedule.getFacilities().containsKey(Id.create(childId, TransitStopFacility.class))) {
                TransitStopFacility child = ChildStopCreator.createChild(
                        facility, facility.getLinkId(), facility.getCoord());
                schedule.addStopFacility(child);
            }
        }

        return new MappingReport(route.getId(), networkRoute, artificialIds, List.of());
    }
}
