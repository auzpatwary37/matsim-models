package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.population.Activity;
import com.citymodeler.matsim.models.population.Leg;
import com.citymodeler.matsim.models.population.NetworkRoute;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.population.Plan;
import com.citymodeler.matsim.models.population.PlanElement;
import com.citymodeler.matsim.models.population.Population;
import com.citymodeler.matsim.models.population.TransitPassengerRoute;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

public final class PopulationXmlReader {
    public Population read(Path path) {
        return read(XmlSupport.parse(path).getDocumentElement());
    }

    public Population read(InputStream inputStream) {
        return read(XmlSupport.parse(inputStream).getDocumentElement());
    }

    public Population readString(String xml) {
        return read(XmlSupport.parse(xml).getDocumentElement());
    }

    private Population read(Element root) {
        Population population = new Population();
        XmlSupport.readAttributes(root, population.getAttributes());
        for (Element personElement : XmlSupport.children(root, "person")) {
            Person person = readPerson(personElement);
            population.addPerson(person);
            person.postProcess();
        }
        return population;
    }

    private Person readPerson(Element personElement) {
        Person person = new Person(Id.create(XmlSupport.attr(personElement, "id"), Person.class));
        XmlSupport.readAttributes(personElement, person.getAttributes());

        for (Element planElement : XmlSupport.children(personElement, "plan")) {
            Plan plan = readPlan(planElement);
            person.addPlan(plan);
            if ("yes".equals(XmlSupport.attr(planElement, "selected"))) {
                person.setSelectedPlan(plan);
            }
        }
        return person;
    }

    private Plan readPlan(Element planElement) {
        Plan plan = new Plan();
        XmlSupport.readAttributes(planElement, plan.getAttributes());
        if ("yes".equals(XmlSupport.attr(planElement, "selected"))) {
            plan.setSelected(true);
        }

        for (Element childElement : XmlSupport.children(planElement, null)) {
            String tagName = childElement.getTagName();
            if ("activity".equals(tagName)) {
                plan.addPlanElement(readActivity(childElement));
            } else if ("leg".equals(tagName)) {
                plan.addPlanElement(readLeg(childElement));
            }
        }
        return plan;
    }

    private Activity readActivity(Element activityElement) {
        Id<ActivityFacility> facilityId = Id.create(XmlSupport.attr(activityElement, "facility"), ActivityFacility.class);
        String type = XmlSupport.attr(activityElement, "type");
        double x = XmlSupport.optionalDouble(activityElement, "x", 0.0);
        double y = XmlSupport.optionalDouble(activityElement, "y", 0.0);
        String endTime = XmlSupport.attr(activityElement, "end_time");

        Activity activity = new Activity(facilityId, type, endTime, x, y);
        XmlSupport.readAttributes(activityElement, activity.getAttributes());
        return activity;
    }

    private Leg readLeg(Element legElement) {
        Leg leg = new Leg(XmlSupport.attr(legElement, "mode"));
        XmlSupport.readAttributes(legElement, leg.getAttributes());

        Element routeElement = XmlSupport.child(legElement, "route");
        if (routeElement != null) {
            String routeType = XmlSupport.attr(routeElement, "type");
            if (isNetworkRoute(routeType)) {
                NetworkRoute route = new NetworkRoute();
                String startLink = firstNonBlank(
                        XmlSupport.attr(routeElement, "start_link"),
                        XmlSupport.attr(routeElement, "startLink"));
                if (startLink != null) {
                    route.setStartLinkId(Id.create(startLink, Link.class));
                }
                String endLink = firstNonBlank(
                        XmlSupport.attr(routeElement, "end_link"),
                        XmlSupport.attr(routeElement, "endLink"));
                if (endLink != null) {
                    route.setEndLinkId(Id.create(endLink, Link.class));
                }
                String links = firstNonBlank(
                        XmlSupport.attr(routeElement, "links"),
                        childText(routeElement, "links"));
                if (links != null && !links.isBlank()) {
                    Arrays.stream(links.split("[\\s,]+"))
                            .filter(s -> !s.isBlank())
                            .map(s -> Id.create(s, Link.class))
                            .forEach(route::addLinkId);
                }
                String travelTime = XmlSupport.attr(routeElement, "travel_time");
                if (travelTime != null) {
                    route.setTravelTime(Double.parseDouble(travelTime));
                }
                String distance = XmlSupport.attr(routeElement, "distance");
                if (distance != null) {
                    route.setDistance(Double.parseDouble(distance));
                }
                leg.setRoute(route);
            } else if (isTransitPassengerRoute(routeType)) {
                TransitPassengerRoute route = new TransitPassengerRoute();
                String accessStopId = firstNonBlank(
                        XmlSupport.attr(routeElement, "accessStopId"),
                        XmlSupport.attr(routeElement, "accessStop"));
                if (accessStopId != null) {
                    route.setAccessStopId(Id.create(accessStopId, TransitStopFacility.class));
                }
                String egressStopId = firstNonBlank(
                        XmlSupport.attr(routeElement, "egressStopId"),
                        XmlSupport.attr(routeElement, "egressStop"));
                if (egressStopId != null) {
                    route.setEgressStopId(Id.create(egressStopId, TransitStopFacility.class));
                }
                Element transitRouteElement = XmlSupport.child(routeElement, "transitRoute");
                if (transitRouteElement != null) {
                    String lineId = XmlSupport.attr(transitRouteElement, "line");
                    if (lineId != null) {
                        route.setLineId(Id.create(lineId, TransitLine.class));
                    }
                    String routeId = XmlSupport.attr(transitRouteElement, "route");
                    if (routeId != null) {
                        route.setRouteId(Id.create(routeId, TransitRoute.class));
                    }
                    String departureId = XmlSupport.attr(transitRouteElement, "departure");
                    if (departureId != null) {
                        route.setDepartureId(Id.create(departureId, Departure.class));
                    }
                    Element accessStopElem = XmlSupport.child(transitRouteElement, "accessStop");
                    if (accessStopElem != null) {
                        String accessStop = XmlSupport.attr(accessStopElem, "stop");
                        if (accessStop != null && route.getAccessStopId() == null) {
                            route.setAccessStopId(Id.create(accessStop, TransitStopFacility.class));
                        }
                    }
                    Element egressStopElem = XmlSupport.child(transitRouteElement, "egressStop");
                    if (egressStopElem != null) {
                        String egressStop = XmlSupport.attr(egressStopElem, "stop");
                        if (egressStop != null && route.getEgressStopId() == null) {
                            route.setEgressStopId(Id.create(egressStop, TransitStopFacility.class));
                        }
                    }
                } else {
                    String lineId = XmlSupport.attr(routeElement, "lineId");
                    if (lineId != null) {
                        route.setLineId(Id.create(lineId, TransitLine.class));
                    }
                    String routeId = XmlSupport.attr(routeElement, "routeId");
                    if (routeId != null) {
                        route.setRouteId(Id.create(routeId, TransitRoute.class));
                    }
                    String departureId = XmlSupport.attr(routeElement, "departureId");
                    if (departureId != null) {
                        route.setDepartureId(Id.create(departureId, Departure.class));
                    }
                }
                leg.setRoute(route);
            }
        }
        return leg;
    }

    private boolean isNetworkRoute(String type) {
        return "NetworkRoute".equals(type) || "links".equals(type);
    }

    private boolean isTransitPassengerRoute(String type) {
        return "TransitPassengerRoute".equals(type) || "pt".equals(type);
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String childText(Element parent, String childName) {
        Element child = XmlSupport.child(parent, childName);
        return child != null ? child.getTextContent() : null;
    }
}