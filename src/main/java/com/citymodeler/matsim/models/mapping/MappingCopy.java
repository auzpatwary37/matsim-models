package com.citymodeler.matsim.models.mapping;

import java.util.ArrayList;
import java.util.Map;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

/**
 * Deep-copy helpers so transit mapping can honor the build-new contract: mapping must construct and
 * return new {@code TransitSchedule} and {@code Network} instances and leave its inputs unmodified.
 */
final class MappingCopy {

    private MappingCopy() {
    }

    static Network copyNetwork(Network source) {
        Network copy = new Network(source.getName());
        copy.getAttributes().getAsMap().forEach((k, v) -> copy.getAttributes().putAttribute(k, v));
        for (Node node : source.getNodes().values()) {
            Node n = new Node(node.getId(), new Coord(node.getCoord().getX(), node.getCoord().getY()));
            node.getAttributes().getAsMap().forEach((k, v) -> n.getAttributes().putAttribute(k, v));
            copy.addNode(n);
        }
        for (Link link : source.getLinks().values()) {
            Link l = new Link(link.getId(), link.getFromNodeId(), link.getToNodeId(),
                    link.getLength(), link.getCapacity(), link.getFreespeed(),
                    link.getNumberOfLanes(), link.getAllowedModes());
            link.getAttributes().getAsMap().forEach((k, v) -> l.getAttributes().putAttribute(k, v));
            copy.addLink(l);
        }
        copy.postProcess();
        return copy;
    }

    static TransitSchedule copySchedule(TransitSchedule source) {
        TransitSchedule copy = new TransitSchedule();
        source.getAttributes().getAsMap().forEach((k, v) -> copy.getAttributes().putAttribute(k, v));

        for (TransitStopFacility f : source.getFacilities().values()) {
            TransitStopFacility nf = new TransitStopFacility(f.getId(),
                    new Coord(f.getCoord().getX(), f.getCoord().getY()), f.isBlockingLane());
            nf.setName(f.getName());
            nf.setLinkId(f.getLinkId());
            nf.setStopAreaId(f.getStopAreaId());
            f.getAttributes().getAsMap().forEach((k, v) -> nf.getAttributes().putAttribute(k, v));
            copy.addStopFacility(nf);
        }

        for (TransitLine line : source.getTransitLines().values()) {
            TransitLine nl = new TransitLine(line.getId());
            nl.setName(line.getName());
            line.getAttributes().getAsMap().forEach((k, v) -> nl.getAttributes().putAttribute(k, v));
            for (TransitRoute route : line.getRoutes().values()) {
                TransitRoute nr = new TransitRoute(route.getId());
                nr.setDescription(route.getDescription());
                nr.setTransportMode(route.getTransportMode());
                if (route.getNetworkRoute() != null) {
                    nr.setNetworkRoute(new ArrayList<>(route.getNetworkRoute()));
                }
                route.getAttributes().getAsMap().forEach((k, v) -> nr.getAttributes().putAttribute(k, v));
                for (TransitRouteStop stop : route.getStops()) {
                    nr.addStop(new TransitRouteStop(stop.getStopFacilityId(),
                            stop.getArrivalOffset(), stop.getDepartureOffset(), stop.isAwaitDeparture()));
                }
                for (Map.Entry<Id<Departure>, Departure> e : route.getDepartures().entrySet()) {
                    Departure d = new Departure(e.getValue().getId(), e.getValue().getDepartureTime());
                    d.setVehicleId(e.getValue().getVehicleId());
                    nr.addDeparture(d);
                }
                nl.addRoute(nr);
            }
            copy.addTransitLine(nl);
        }
        copy.postProcess();
        return copy;
    }
}
