package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.StringJoiner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.population.Activity;
import com.citymodeler.matsim.models.population.Leg;
import com.citymodeler.matsim.models.population.NetworkRoute;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.population.Plan;
import com.citymodeler.matsim.models.population.PlanElement;
import com.citymodeler.matsim.models.population.Population;
import com.citymodeler.matsim.models.population.TransitPassengerRoute;

public final class PopulationXmlWriter {
    public void write(Population population, Path path) {
        XmlSupport.write(document(population), path);
    }

    public void write(Population population, OutputStream outputStream) {
        XmlSupport.write(document(population), outputStream);
    }

    public String writeToString(Population population) {
        return XmlSupport.writeToString(document(population));
    }

    private Document document(Population population) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElement("population");
        document.appendChild(root);

        for (Person person : population.getPersons().values()) {
            Element personElement = document.createElement("person");
            personElement.setAttribute("id", person.getId().toString());
            XmlSupport.appendAttributes(document, personElement, person.getAttributes());
            root.appendChild(personElement);

            for (Plan plan : person.getPlans()) {
                Element planElement = document.createElement("plan");
                planElement.setAttribute("selected", plan.isSelected() ? "yes" : "no");
                XmlSupport.appendAttributes(document, planElement, plan.getAttributes());
                personElement.appendChild(planElement);

                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity activity) {
                        Element activityElement = document.createElement("activity");
                        activityElement.setAttribute("facility", activity.getFacilityId().toString());
                        activityElement.setAttribute("type", activity.getType());
                        activityElement.setAttribute("x", Double.toString(activity.getCoord().getX()));
                        activityElement.setAttribute("y", Double.toString(activity.getCoord().getY()));
                        XmlSupport.setIfPresent(activityElement, "end_time", activity.getEndTime());
                        XmlSupport.appendAttributes(document, activityElement, activity.getAttributes());
                        planElement.appendChild(activityElement);
                    } else if (element instanceof Leg leg) {
                        Element legElement = document.createElement("leg");
                        legElement.setAttribute("mode", leg.getMode());
                        if (leg.getRoute() != null) {
                            if (leg.getRoute() instanceof NetworkRoute networkRoute) {
                                Element routeElement = document.createElement("route");
                                routeElement.setAttribute("type", "NetworkRoute");
                                routeElement.setAttribute("start_link", networkRoute.getStartLinkId().toString());
                                routeElement.setAttribute("end_link", networkRoute.getEndLinkId().toString());
                                StringJoiner linkJoiner = new StringJoiner(" ");
                                networkRoute.getLinkIds().forEach(link -> linkJoiner.add(link.toString()));
                                routeElement.setAttribute("links", linkJoiner.toString());
                                if (networkRoute.getTravelTime() > 0) {
                                    routeElement.setAttribute("travel_time", Double.toString(networkRoute.getTravelTime()));
                                }
                                if (networkRoute.getDistance() > 0) {
                                    routeElement.setAttribute("distance", Double.toString(networkRoute.getDistance()));
                                }
                                legElement.appendChild(routeElement);
                            } else if (leg.getRoute() instanceof TransitPassengerRoute ptRoute) {
                                Element routeElement = document.createElement("route");
                                routeElement.setAttribute("type", "TransitPassengerRoute");
                                routeElement.setAttribute("accessStopId", ptRoute.getAccessStopId().toString());
                                routeElement.setAttribute("egressStopId", ptRoute.getEgressStopId().toString());
                                routeElement.setAttribute("lineId", ptRoute.getLineId().toString());
                                routeElement.setAttribute("routeId", ptRoute.getRouteId().toString());
                                routeElement.setAttribute("departureId", ptRoute.getDepartureId().toString());
                                legElement.appendChild(routeElement);
                            }
                        }
                        XmlSupport.appendAttributes(document, legElement, leg.getAttributes());
                        planElement.appendChild(legElement);
                    }
                }
            }
        }
        return document;
    }
}