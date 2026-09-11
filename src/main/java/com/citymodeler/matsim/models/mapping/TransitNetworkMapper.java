package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps a transit schedule onto a road network.
 *
 * <p>For each transit route:
 * <ol>
 *   <li>Projects GTFS stop coordinates into the network's CRS, assigns the best mode-compatible link</li>
 *   <li>Finds least-cost distance paths between adjacent stop links (Dijkstra with turn restrictions)</li>
 *   <li>Creates artificial connectors where no path exists or path exceeds the cap</li>
 *   <li>Assembles a continuous network route</li>
 *   <li>Creates child stop facilities per (parent, link) and rewires the route stops to them</li>
 * </ol>
 */
public final class TransitNetworkMapper {

    private final TransitMappingConfig config;
    private final LinkSpatialIndex spatialIndex;

    public TransitNetworkMapper(TransitMappingConfig config,
                                LinkSpatialIndex spatialIndex) {
        this.config = config;
        this.spatialIndex = spatialIndex;
        config.validate();
    }

    public TransitMappingResult map(TransitSchedule schedule, Network network) {
        List<String> warnings = new ArrayList<>();
        List<MappingReport> reports = new ArrayList<>();
        StopCandidateScorer scorer = new StopCandidateScorer(config, spatialIndex, network);
        ArtificialLinkFactory linkFactory = new ArtificialLinkFactory(network);
        Set<Id<Link>> artificialLinkIds = new HashSet<>();
        CrsUtils.Projector projector = CrsUtils.forCrs(CrsUtils.networkTargetCrs(network));

        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                MappingReport report = mapRoute(schedule, network, route, scorer, linkFactory,
                        artificialLinkIds, projector, warnings);
                reports.add(report);
            }
        }

        schedule.postProcess();
        return new TransitMappingResult(schedule, network, reports, warnings);
    }

    private MappingReport mapRoute(TransitSchedule schedule, Network network,
                                    TransitRoute route, StopCandidateScorer scorer,
                                    ArtificialLinkFactory factory, Set<Id<Link>> artificialIds,
                                    CrsUtils.Projector projector, List<String> warnings) {
        // Review #6: the actual transport mode drives access + restriction evaluation.
        String mode = route.getTransportMode() != null ? route.getTransportMode() : "pt";
        List<TransitRouteStop> stops = route.getStops();
        List<String> createdArtificialIds = new ArrayList<>();

        if (stops.isEmpty()) {
            return new MappingReport(route.getId(), List.of(), List.of(), List.of("empty route"));
        }

        // Step 1: Assign best candidate link to each stop
        Map<Id<TransitStopFacility>, Id<Link>> stopLinks = new LinkedHashMap<>();
        for (TransitRouteStop stop : stops) {
            TransitStopFacility facility = schedule.getFacilities().get(stop.getStopFacilityId());
            if (facility == null) continue;

            List<StopCandidate> candidates = scorer.score(projectedStop(facility, projector), mode);
            if (candidates.isEmpty()) {
                // No candidate: create artificial loop
                String ctx = facility.getId().toString() + "_" + route.getId().toString();
                Link loop = factory.createLoop(facility.getCoord(), mode, ctx);
                facility.setLinkId(loop.getId());
                stopLinks.put(stop.getStopFacilityId(), loop.getId());
                createdArtificialIds.add(loop.getId().toString());
                artificialIds.add(loop.getId());
                warnings.add("No candidate links for stop " + facility.getId() + ", created loop");
            } else {
                StopCandidate best = candidates.get(0);
                facility.setLinkId(best.linkId());
                stopLinks.put(stop.getStopFacilityId(), best.linkId());
            }
        }

        // Step 2: Build continuous network route by routing between adjacent stop links
        List<Id<Link>> networkRoute = new ArrayList<>();
        List<Id<Link>> stopLinkIds = new ArrayList<>();
        for (TransitRouteStop stop : stops) {
            Id<Link> linkId = stopLinks.get(stop.getStopFacilityId());
            if (linkId != null) stopLinkIds.add(linkId);
        }

        // Deduplicate consecutive same links
        List<Id<Link>> uniqueStopLinks = new ArrayList<>();
        for (Id<Link> sl : stopLinkIds) {
            if (uniqueStopLinks.isEmpty() || !uniqueStopLinks.get(uniqueStopLinks.size() - 1).equals(sl)) {
                uniqueStopLinks.add(sl);
            }
        }

        boolean continuous = true;
        if (!uniqueStopLinks.isEmpty()) {
            RoutePathFinder pathFinder = new RoutePathFinder(
                    network, artificialIds, config.routingWithCandidateDistance(), mode);

            networkRoute.add(uniqueStopLinks.get(0));

            for (int i = 1; i < uniqueStopLinks.size(); i++) {
                Id<Link> fromLink = uniqueStopLinks.get(i - 1);
                Id<Link> toLink = uniqueStopLinks.get(i);

                if (fromLink.equals(toLink)) continue;

                List<Id<Link>> path = pathFinder.findPath(fromLink, toLink);
                if (path != null && !path.isEmpty()) {
                    // Append path excluding the first link (already in networkRoute)
                    for (int j = 1; j < path.size(); j++) {
                        networkRoute.add(path.get(j));
                    }
                } else {
                    // No path found: create an artificial connector bridging the two links.
                    Link fromL = network.getLinks().get(fromLink);
                    Link toL = network.getLinks().get(toLink);
                    if (fromL != null && toL != null && fromL.getToNode() != null && toL.getFromNode() != null) {
                        String ctx = route.getId().toString() + "_" + fromLink + "_" + toLink;
                        Link connector = factory.createConnector(
                                fromL.getToNode(), toL.getFromNode(), mode, ctx);
                        createdArtificialIds.add(connector.getId().toString());
                        artificialIds.add(connector.getId());
                        networkRoute.add(connector.getId());
                        networkRoute.add(toLink);
                        warnings.add("No path between " + fromLink + " and " + toLink
                                + ", created connector " + connector.getId());
                    } else {
                        // Review #13: cannot create a valid connector (missing node refs). Do NOT
                        // append a disconnected link; mark the route unmapped instead of emitting a
                        // discontinuous network route.
                        continuous = false;
                        warnings.add("Unmappable route " + route.getId()
                                + ": cannot connect " + fromLink + " to " + toLink
                                + " (missing node references); no network route emitted");
                        break;
                    }
                }
            }
        }

        if (continuous && !networkRoute.isEmpty()) {
            route.setNetworkRoute(networkRoute);
        } else {
            route.setNetworkRoute(null);
        }

        // Step 3: Create child stop facilities and rewire route stops to them (review #2).
        List<TransitRouteStop> newStops = new ArrayList<>();
        for (TransitRouteStop stop : stops) {
            TransitStopFacility facility = schedule.getFacilities().get(stop.getStopFacilityId());
            Id<Link> linkId = facility != null ? stopLinks.get(stop.getStopFacilityId()) : null;
            if (facility == null || linkId == null) {
                newStops.add(stop);
                continue;
            }

            String childIdStr = ChildStopCreator.childStopId(facility.getId().toString(), linkId.toString());
            Id<TransitStopFacility> childId = Id.create(childIdStr, TransitStopFacility.class);

            if (!schedule.getFacilities().containsKey(childId)) {
                TransitStopFacility child = ChildStopCreator.createChild(facility, linkId, facility.getCoord());
                schedule.addStopFacility(child);
            }

            newStops.add(new TransitRouteStop(childId,
                    stop.getArrivalOffset(), stop.getDepartureOffset(), stop.isAwaitDeparture()));
        }

        rewireRouteStops(route, newStops);

        List<String> issues = continuous ? List.of() : List.of("unmapped: discontinuous path prevented");
        return new MappingReport(route.getId(),
                continuous ? networkRoute : List.of(),
                createdArtificialIds, issues);
    }

    /**
     * Projects a GTFS stop into the network's CRS using the shared projection contract (review #1).
     * The stop's WGS84 coordinates are read from the {@code gtfs:lon}/{@code gtfs:lat} attributes
     * written by the schedule builder; when those are absent the facility is assumed to already be
     * in the network's CRS and is returned unchanged. No inference from coordinate magnitude.
     */
    private TransitStopFacility projectedStop(TransitStopFacility facility, CrsUtils.Projector projector) {
        Object lon = facility.getAttributes().getAttribute("gtfs:lon");
        Object lat = facility.getAttributes().getAttribute("gtfs:lat");
        if (lon == null || lat == null) {
            return facility;
        }
        double stopLon = ((Number) lon).doubleValue();
        double stopLat = ((Number) lat).doubleValue();
        Coord projected = projector.project(stopLon, stopLat);
        return new TransitStopFacility(facility.getId(), projected, facility.isBlockingLane());
    }

    private void rewireRouteStops(TransitRoute route, List<TransitRouteStop> newStops) {
        // Review #2: actually replace the route's stop references with the child-facility versions so
        // route-specific (parent, link) assignments are represented in the TransitRouteStop list.
        route.setStops(newStops);
    }
}
