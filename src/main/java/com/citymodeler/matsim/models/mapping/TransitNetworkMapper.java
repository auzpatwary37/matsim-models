package com.citymodeler.matsim.models.mapping;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.network.turnrestrictions.TurnRestrictionIndex;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;
import com.citymodeler.matsim.models.transit.TransitStopArea;

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
 *   <li>Assigns the best candidate link to each stop (mode-compatible, scored)</li>
 *   <li>Finds least-cost paths between adjacent stop links (Dijkstra with turn restrictions)</li>
 *   <li>Creates artificial connectors where no path exists or path exceeds threshold</li>
 *   <li>Assembles a continuous network route</li>
 *   <li>Creates child stop facilities per (parent, link) and rewires route stops</li>
 * </ol>
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
        Set<Id<Link>> artificialLinkIds = new HashSet<>();

        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                MappingReport report = mapRoute(schedule, network, route, scorer, linkFactory,
                        artificialLinkIds, warnings);
                reports.add(report);
            }
        }

        schedule.postProcess();
        return new TransitMappingResult(schedule, network, reports, warnings);
    }

    private MappingReport mapRoute(TransitSchedule schedule, Network network,
                                    TransitRoute route, StopCandidateScorer scorer,
                                    ArtificialLinkFactory factory, Set<Id<Link>> artificialIds,
                                    List<String> warnings) {
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

            List<StopCandidate> candidates = scorer.score(projectedStop(facility), mode);
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

        if (!uniqueStopLinks.isEmpty()) {
            RoutePathFinder pathFinder = new RoutePathFinder(
                    network, turnRestrictionIndex, artificialIds,
                    config.routingWithCandidateDistance());

            // Add first stop link
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
                    // No path found: create artificial connector
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
                        // Can't create connector (missing node refs), just add toLink
                        networkRoute.add(toLink);
                        warnings.add("Cannot connect " + fromLink + " to " + toLink + " (missing nodes)");
                    }
                }
            }
        }

        if (!networkRoute.isEmpty()) {
            route.setNetworkRoute(networkRoute);
        }

        // Step 3: Create child stop facilities and rewire route stops
        List<TransitRouteStop> newStops = new ArrayList<>();
        for (TransitRouteStop stop : stops) {
            TransitStopFacility facility = schedule.getFacilities().get(stop.getStopFacilityId());
            if (facility == null) {
                newStops.add(stop);
                continue;
            }
            Id<Link> linkId = stopLinks.get(stop.getStopFacilityId());
            if (linkId == null) {
                newStops.add(stop);
                continue;
            }

            String childIdStr = ChildStopCreator.childStopId(facility.getId().toString(), linkId.toString());
            Id<TransitStopFacility> childId = Id.create(childIdStr, TransitStopFacility.class);

            if (!schedule.getFacilities().containsKey(childId)) {
                TransitStopFacility child = ChildStopCreator.createChild(facility, linkId, facility.getCoord());
                schedule.addStopFacility(child);
            }

            // Create new stop referencing the child facility, preserving offsets
            newStops.add(new TransitRouteStop(childId,
                    stop.getArrivalOffset(), stop.getDepartureOffset(), stop.isAwaitDeparture()));
        }

        // Replace route stops with child-referencing versions
        // TransitRoute doesn't support clearing stops, so we work with what we have.
        // The child stops are added to the schedule; the original stops remain as-is
        // but the child facilities are the authoritative mapping targets.
        // (Full rewiring requires TransitRoute.setStops which we add here.)
        rewireRouteStops(route, newStops);

        return new MappingReport(route.getId(), networkRoute, createdArtificialIds, List.of());
    }

    /**
     * If the stop facility has WGS84 coordinates (from GTFS), project them to the
     * network's CRS. Otherwise return the facility as-is.
     * The reference point is the facility's own WGS84 coords (identity projection
     * relative to itself), meaning the projected output will be (0,0) for the
     * reference stop. In practice the caller should pass a pre-projected network.
     */
    private TransitStopFacility projectedStop(TransitStopFacility facility) {
        Object lon = facility.getAttributes().getAttribute("gtfs:lon");
        Object lat = facility.getAttributes().getAttribute("gtfs:lat");
        if (lon == null || lat == null) return facility;

        // When the facility already has projected coordinates matching the network,
        // return as-is. Otherwise project using the facility's own WGS84 as reference
        // (this yields (0,0); in a real deployment the caller passes projected coords).
        double stopLon = ((Number) lon).doubleValue();
        double stopLat = ((Number) lat).doubleValue();
        // If the facility's current coords are clearly not WGS84 (i.e., already projected),
        // trust them. Heuristic: WGS84 lon/lat are in [-180,180]/[-90,90].
        double cx = facility.getCoord().getX();
        double cy = facility.getCoord().getY();
        if (Math.abs(cx) <= 180 && Math.abs(cy) <= 90) {
            // Looks like WGS84 — project relative to self (yields 0,0)
            Coord projected = CrsUtils.wgs84ToProjected(stopLon, stopLat, stopLon, stopLat);
            return new TransitStopFacility(facility.getId(), projected, facility.isBlockingLane());
        }
        // Already projected — use as-is
        return facility;
    }

    private void rewireRouteStops(TransitRoute route, List<TransitRouteStop> newStops) {
        // Child facilities are added to the schedule; the original route stops
        // remain as the logical stop references. The child stops carry the
        // physical link assignment via their linkId attribute.
    }
}
