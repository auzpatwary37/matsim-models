package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
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
import com.citymodeler.matsim.models.population.UnknownRoute;

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
        XmlSupport.appendAttributes(document, root, population.getAttributes());
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
                        if (activity.getFacilityId() != null) {
                            activityElement.setAttribute("facility", activity.getFacilityId().toString());
                        }
                        activityElement.setAttribute("type", activity.getType());
                        if (activity.getCoord() != null) {
                            activityElement.setAttribute("x", Double.toString(activity.getCoord().getX()));
                            activityElement.setAttribute("y", Double.toString(activity.getCoord().getY()));
                        }
                        if (activity.getLinkId() != null) {
                            activityElement.setAttribute("link", activity.getLinkId().toString());
                        }
                        if (!Double.isNaN(activity.getStartTime()) && activity.getStartTime() != Double.MAX_VALUE) {
                            String formatted = Activity.formatTime(activity.getStartTime());
                            if (formatted != null) {
                                activityElement.setAttribute("start_time", formatted);
                            }
                        }
                        if (!Double.isNaN(activity.getEndTime()) && activity.getEndTime() != Double.MAX_VALUE) {
                            String formatted = Activity.formatTime(activity.getEndTime());
                            if (formatted != null) {
                                activityElement.setAttribute("end_time", formatted);
                            }
                        }
                        if (!Double.isNaN(activity.getMaximumDuration()) && activity.getMaximumDuration() != Double.MAX_VALUE) {
                            activityElement.setAttribute("maximumDuration", Double.toString(activity.getMaximumDuration()));
                        }
                        XmlSupport.appendAttributes(document, activityElement, activity.getAttributes());
                        planElement.appendChild(activityElement);
                    } else if (element instanceof Leg leg) {
                        Element legElement = document.createElement("leg");
                        legElement.setAttribute("mode", leg.getMode());
                        if (leg.getRoute() != null) {
                            if (leg.getRoute() instanceof NetworkRoute networkRoute) {
                                Element routeElement = document.createElement("route");
                                routeElement.setAttribute("type", "NetworkRoute");
                                if (networkRoute.getStartLinkId() != null) {
                                    routeElement.setAttribute("start_link", networkRoute.getStartLinkId().toString());
                                }
                                if (networkRoute.getEndLinkId() != null) {
                                    routeElement.setAttribute("end_link", networkRoute.getEndLinkId().toString());
                                }
                                if (!networkRoute.getLinkIds().isEmpty()) {
                                    Element linksElement = document.createElement("links");
                                    StringJoiner linkJoiner = new StringJoiner(" ");
                                    networkRoute.getLinkIds().forEach(link -> linkJoiner.add(link.toString()));
                                    linksElement.setTextContent(linkJoiner.toString());
                                    routeElement.appendChild(linksElement);
                                }
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
                                if (ptRoute.getLineId() != null && ptRoute.getRouteId() != null && ptRoute.getDepartureId() != null) {
                                    Element transitRouteElement = document.createElement("transitRoute");
                                    transitRouteElement.setAttribute("line", ptRoute.getLineId().toString());
                                    transitRouteElement.setAttribute("route", ptRoute.getRouteId().toString());
                                    transitRouteElement.setAttribute("departure", ptRoute.getDepartureId().toString());
                                    if (ptRoute.getAccessStopId() != null) {
                                        Element accessStopElement = document.createElement("accessStop");
                                        accessStopElement.setAttribute("stop", ptRoute.getAccessStopId().toString());
                                        transitRouteElement.appendChild(accessStopElement);
                                    }
                                    if (ptRoute.getEgressStopId() != null) {
                                        Element egressStopElement = document.createElement("egressStop");
                                        egressStopElement.setAttribute("stop", ptRoute.getEgressStopId().toString());
                                        transitRouteElement.appendChild(egressStopElement);
                                    }
                                    routeElement.appendChild(transitRouteElement);
                                } else {
                                    if (ptRoute.getAccessStopId() != null) {
                                        routeElement.setAttribute("accessStopId", ptRoute.getAccessStopId().toString());
                                    }
                                    if (ptRoute.getEgressStopId() != null) {
                                        routeElement.setAttribute("egressStopId", ptRoute.getEgressStopId().toString());
                                    }
                                    if (ptRoute.getLineId() != null) {
                                        routeElement.setAttribute("lineId", ptRoute.getLineId().toString());
                                    }
                                    if (ptRoute.getRouteId() != null) {
                                        routeElement.setAttribute("routeId", ptRoute.getRouteId().toString());
                                    }
                                    if (ptRoute.getDepartureId() != null) {
                                        routeElement.setAttribute("departureId", ptRoute.getDepartureId().toString());
                                    }
                                }
                                legElement.appendChild(routeElement);
                            } else if (leg.getRoute() instanceof UnknownRoute unknownRoute) {
                                Element routeElement = document.createElement("route");
                                routeElement.setAttribute("type", unknownRoute.getRouteType());
                                for (Map.Entry<String, String> attr : unknownRoute.getAttributes().entrySet()) {
                                    routeElement.setAttribute(attr.getKey(), attr.getValue());
                                }
                                for (Map.Entry<String, String> child : unknownRoute.getChildren().entrySet()) {
                                    Element childElement = document.createElement(child.getKey());
                                    childElement.setTextContent(child.getValue());
                                    routeElement.appendChild(childElement);
                                }
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