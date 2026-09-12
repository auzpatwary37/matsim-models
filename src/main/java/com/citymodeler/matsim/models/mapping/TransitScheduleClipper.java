package com.citymodeler.matsim.models.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

/**
 * Clips a transit schedule to the geographic extent of a network.
 *
 * <p>When a GTFS feed covers a larger area than the OSM network built for it (for example a national
 * feed mapped onto a city extract), every out-of-area stop has no nearby link and the mapper
 * fabricates artificial links for it — hundreds of thousands of them. Clipping the schedule to the
 * network's bounding box (plus a configurable margin) first removes stops the network cannot
 * possibly serve, so any remaining artificial links reflect genuine unreachability inside the
 * network rather than a coverage mismatch.
 *
 * <p>Build-new: the input schedule is never modified; a new schedule is returned. Stop coordinates
 * are projected WGS84→network CRS before the bounds test (same projection contract as the mapper).
 * A route is kept when at least {@code minStopsPerRoute} of its stops survive; a line is kept when
 * at least one of its routes survives.
 */
public final class TransitScheduleClipper {

    private TransitScheduleClipper() {
    }

    /** Result of clipping: the retained schedule and how much was dropped. */
    public record ClipResult(TransitSchedule schedule, int stopsKept, int stopsDropped,
                             int routesDropped) {
    }

    public static ClipResult clipToNetwork(TransitSchedule source, Network network) {
        return clipToNetwork(source, network, 0.0);
    }

    /**
     * @param marginMeters extra margin added around the network bounding box (projected metres)
     */
    public static ClipResult clipToNetwork(TransitSchedule source, Network network, double marginMeters) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (var n : network.getNodes().values()) {
            Coord c = n.getCoord();
            minX = Math.min(minX, c.getX());
            maxX = Math.max(maxX, c.getX());
            minY = Math.min(minY, c.getY());
            maxY = Math.max(maxY, c.getY());
        }
        minX -= marginMeters; maxX += marginMeters;
        minY -= marginMeters; maxY += marginMeters;

        CrsUtils.Projector projector = CrsUtils.forCrs(CrsUtils.networkTargetCrs(network));

        Set<Id<TransitStopFacility>> keep = new HashSet<>();
        TransitSchedule out = new TransitSchedule();
        source.getAttributes().getAsMap().forEach((k, v) -> out.getAttributes().putAttribute(k, v));

        int kept = 0, dropped = 0;
        for (TransitStopFacility f : source.getFacilities().values()) {
            Coord c = projected(f, projector);
            boolean inside = c.getX() >= minX && c.getX() <= maxX
                    && c.getY() >= minY && c.getY() <= maxY;
            if (inside) {
                out.addStopFacility(copyFacility(f));
                keep.add(f.getId());
                kept++;
            } else {
                dropped++;
            }
        }

        int routesDropped = 0;
        for (TransitLine line : source.getTransitLines().values()) {
            TransitLine nl = new TransitLine(line.getId());
            nl.setName(line.getName());
            line.getAttributes().getAsMap().forEach((k, v) -> nl.getAttributes().putAttribute(k, v));
            boolean anyRoute = false;
            for (TransitRoute route : line.getRoutes().values()) {
                List<TransitRouteStop> keptStops = new ArrayList<>();
                for (TransitRouteStop s : route.getStops()) {
                    if (keep.contains(s.getStopFacilityId())) {
                        keptStops.add(new TransitRouteStop(s.getStopFacilityId(),
                                s.getArrivalOffset(), s.getDepartureOffset(), s.isAwaitDeparture()));
                    }
                }
                if (keptStops.size() < 2) {
                    routesDropped++;
                    continue;
                }
                TransitRoute nr = new TransitRoute(route.getId());
                nr.setDescription(route.getDescription());
                nr.setTransportMode(route.getTransportMode());
                route.getAttributes().getAsMap().forEach((k, v) -> nr.getAttributes().putAttribute(k, v));
                for (TransitRouteStop s : keptStops) {
                    nr.addStop(s);
                }
                for (var d : route.getDepartures().values()) {
                    var nd = new com.citymodeler.matsim.models.transit.Departure(d.getId(), d.getDepartureTime());
                    nd.setVehicleId(d.getVehicleId());
                    nr.addDeparture(nd);
                }
                nl.addRoute(nr);
                anyRoute = true;
            }
            if (anyRoute) {
                out.addTransitLine(nl);
            }
        }
        out.postProcess();
        return new ClipResult(out, kept, dropped, routesDropped);
    }

    private static Coord projected(TransitStopFacility f, CrsUtils.Projector projector) {
        Object lon = f.getAttributes().getAttribute("gtfs:lon");
        Object lat = f.getAttributes().getAttribute("gtfs:lat");
        if (lon == null || lat == null) {
            return f.getCoord();
        }
        return projector.project(((Number) lon).doubleValue(), ((Number) lat).doubleValue());
    }

    private static TransitStopFacility copyFacility(TransitStopFacility f) {
        TransitStopFacility nf = new TransitStopFacility(f.getId(),
                new Coord(f.getCoord().getX(), f.getCoord().getY()), f.isBlockingLane());
        nf.setName(f.getName());
        nf.setLinkId(f.getLinkId());
        nf.setStopAreaId(f.getStopAreaId());
        f.getAttributes().getAsMap().forEach((k, v) -> nf.getAttributes().putAttribute(k, v));
        return nf;
    }
}
