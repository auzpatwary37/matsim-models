package com.citymodeler.matsim.models.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.population.Activity;
import com.citymodeler.matsim.models.population.Leg;
import com.citymodeler.matsim.models.population.NetworkRoute;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.population.Plan;
import com.citymodeler.matsim.models.population.Route;
import com.citymodeler.matsim.models.population.TransitPassengerRoute;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

public final class PopulationStaxReader {
    public interface PersonHandler {
        void handle(Person person);
    }

    public void read(Path path, PersonHandler handler) {
        try (InputStream inputStream = XmlSupport.openInputStream(path)) {
            read(inputStream, handler);
        } catch (IOException exception) {
            throw new MatsimParseException("Could not read population from " + path, exception);
        }
    }

    public void read(InputStream inputStream, PersonHandler handler) {
        XMLStreamReader reader = null;
        try {
            XMLInputFactory inputFactory = createSafeInputFactory();
            reader = inputFactory.createXMLStreamReader(inputStream);
            List<Person> persons = readAllPersons(reader);
            for (Person person : persons) {
                handler.handle(person);
            }
        } catch (XMLStreamException exception) {
            throw new MatsimParseException("Could not parse population XML", exception);
        } finally {
            closeQuietly(reader);
        }
    }

    private List<Person> readAllPersons(XMLStreamReader reader) throws XMLStreamException {
        List<Person> persons = new ArrayList<>();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "person".equals(reader.getLocalName())) {
                persons.add(parsePerson(reader));
            }
        }
        return persons;
    }

    private Person parsePerson(XMLStreamReader reader) throws XMLStreamException {
        String personId = reader.getAttributeValue(null, "id");
        Person person = new Person(Id.create(personId, Person.class));

        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("plan".equals(tagName)) {
                    boolean isSelected = "yes".equals(reader.getAttributeValue(null, "selected"));
                    Plan plan = parsePlan(reader);
                    person.addPlan(plan);
                    if (isSelected) {
                        person.setSelectedPlan(plan);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return person;
    }

    private Plan parsePlan(XMLStreamReader reader) throws XMLStreamException {
        Plan plan = new Plan();
        if ("yes".equals(reader.getAttributeValue(null, "selected"))) {
            plan.setSelected(true);
        }

        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("activity".equals(tagName)) {
                    Activity activity = parseActivity(reader);
                    plan.addPlanElement(activity);
                } else if ("leg".equals(tagName)) {
                    Leg leg = parseLeg(reader);
                    plan.addPlanElement(leg);
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return plan;
    }

    private Activity parseActivity(XMLStreamReader reader) throws XMLStreamException {
        String facilityStr = reader.getAttributeValue(null, "facility");
        Id<ActivityFacility> facilityId = facilityStr != null ? Id.create(facilityStr, ActivityFacility.class) : null;
        String type = reader.getAttributeValue(null, "type");
        String endTimeStr = reader.getAttributeValue(null, "end_time");
        String xStr = reader.getAttributeValue(null, "x");
        String yStr = reader.getAttributeValue(null, "y");

        double x = xStr != null ? Double.parseDouble(xStr) : 0.0;
        double y = yStr != null ? Double.parseDouble(yStr) : 0.0;

        Activity activity = new Activity(facilityId, type, endTimeStr, x, y);
        skipElement(reader);
        return activity;
    }

    private Leg parseLeg(XMLStreamReader reader) throws XMLStreamException {
        String mode = reader.getAttributeValue(null, "mode");
        Leg leg = new Leg(mode);

        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("route".equals(tagName)) {
                    Route route = parseRoute(reader);
                    if (route != null) {
                        leg.setRoute(route);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return leg;
    }

    private Route parseRoute(XMLStreamReader reader) throws XMLStreamException {
        String routeType = reader.getAttributeValue(null, "type");
        if (isNetworkRoute(routeType)) {
            return parseNetworkRoute(reader);
        } else if (isTransitPassengerRoute(routeType)) {
            return parseTransitPassengerRoute(reader);
        }
        skipElement(reader);
        return null;
    }

    private NetworkRoute parseNetworkRoute(XMLStreamReader reader) throws XMLStreamException {
        NetworkRoute route = new NetworkRoute();

        String startLink = firstNonBlank(
                reader.getAttributeValue(null, "start_link"),
                reader.getAttributeValue(null, "startLink"));
        if (startLink != null) {
            route.setStartLinkId(Id.create(startLink, Link.class));
        }

        String endLink = firstNonBlank(
                reader.getAttributeValue(null, "end_link"),
                reader.getAttributeValue(null, "endLink"));
        if (endLink != null) {
            route.setEndLinkId(Id.create(endLink, Link.class));
        }

        String links = null;

        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("links".equals(tagName)) {
                    links = reader.getElementText();
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }

        if (links != null && !links.isBlank()) {
            for (String link : links.split("[\\s,]+")) {
                if (!link.isBlank()) {
                    route.addLinkId(Id.create(link, Link.class));
                }
            }
        }

        return route;
    }

    private TransitPassengerRoute parseTransitPassengerRoute(XMLStreamReader reader) throws XMLStreamException {
        TransitPassengerRoute route = new TransitPassengerRoute();

        String accessStopId = firstNonBlank(
                reader.getAttributeValue(null, "accessStopId"),
                reader.getAttributeValue(null, "accessStop"));
        if (accessStopId != null) {
            route.setAccessStopId(Id.create(accessStopId, TransitStopFacility.class));
        }

        String egressStopId = firstNonBlank(
                reader.getAttributeValue(null, "egressStopId"),
                reader.getAttributeValue(null, "egressStop"));
        if (egressStopId != null) {
            route.setEgressStopId(Id.create(egressStopId, TransitStopFacility.class));
        }

        String lineId = reader.getAttributeValue(null, "lineId");
        if (lineId != null) {
            route.setLineId(Id.create(lineId, TransitLine.class));
        }
        String routeId = reader.getAttributeValue(null, "routeId");
        if (routeId != null) {
            route.setRouteId(Id.create(routeId, TransitRoute.class));
        }
        String departureId = reader.getAttributeValue(null, "departureId");
        if (departureId != null) {
            route.setDepartureId(Id.create(departureId, Departure.class));
        }

        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("transitRoute".equals(tagName)) {
                    parseTransitRouteDetails(reader, route);
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }

        return route;
    }

    private void parseTransitRouteDetails(XMLStreamReader reader, TransitPassengerRoute route) throws XMLStreamException {
        String lineId = reader.getAttributeValue(null, "line");
        if (lineId != null) {
            route.setLineId(Id.create(lineId, TransitLine.class));
        }
        String routeId = reader.getAttributeValue(null, "route");
        if (routeId != null) {
            route.setRouteId(Id.create(routeId, TransitRoute.class));
        }
        String departureId = reader.getAttributeValue(null, "departure");
        if (departureId != null) {
            route.setDepartureId(Id.create(departureId, Departure.class));
        }

        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("accessStop".equals(tagName)) {
                    String accessStop = reader.getAttributeValue(null, "stop");
                    if (accessStop != null && route.getAccessStopId() == null) {
                        route.setAccessStopId(Id.create(accessStop, TransitStopFacility.class));
                    }
                    skipElement(reader);
                } else if ("egressStop".equals(tagName)) {
                    String egressStop = reader.getAttributeValue(null, "stop");
                    if (egressStop != null && route.getEgressStopId() == null) {
                        route.setEgressStopId(Id.create(egressStop, TransitStopFacility.class));
                    }
                    skipElement(reader);
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
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

    private void skipElement(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private void closeQuietly(XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException ignored) {
            }
        }
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    public Stream<Person> stream(Path path) {
        InputStream inputStream;
        try {
            inputStream = XmlSupport.openInputStream(path);
        } catch (IOException exception) {
            throw new MatsimParseException("Could not read population from " + path, exception);
        }
        XMLInputFactory inputFactory = createSafeInputFactory();
        try {
            XMLStreamReader reader = inputFactory.createXMLStreamReader(inputStream);
            return StreamSupport.stream(new PersonSpliterator(reader), false)
                    .onClose(() -> {
                        closeQuietly(reader);
                        closeQuietly(inputStream);
                    });
        } catch (XMLStreamException exception) {
            closeQuietly(inputStream);
            throw new MatsimParseException("Could not create XML stream reader", exception);
        }
    }

    private XMLInputFactory createSafeInputFactory() {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        requireProperty(inputFactory, XMLInputFactory.SUPPORT_DTD, false);
        requireProperty(inputFactory, "javax.xml.stream.isSupportingExternalEntities", false);
        setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return inputFactory;
    }

    private void requireProperty(XMLInputFactory inputFactory, String propertyName, Object value) {
        try {
            inputFactory.setProperty(propertyName, value);
        } catch (IllegalArgumentException exception) {
            throw new MatsimModelException("Could not apply required XML parser security property: " + propertyName, exception);
        }
    }

    private void setPropertyIfSupported(XMLInputFactory inputFactory, String propertyName, Object value) {
        try {
            inputFactory.setProperty(propertyName, value);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static class PersonSpliterator implements java.util.Spliterator<Person> {
        private final XMLStreamReader reader;
        private boolean hasNext = true;

        PersonSpliterator(XMLStreamReader reader) {
            this.reader = reader;
        }

        @Override
        public boolean tryAdvance(java.util.function.Consumer<? super Person> action) {
            if (!hasNext) {
                return false;
            }
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT && "person".equals(reader.getLocalName())) {
                        Person person = parsePersonStatic(reader);
                        action.accept(person);
                        return true;
                    }
                }
                hasNext = false;
                return false;
            } catch (XMLStreamException e) {
                throw new MatsimParseException("Error reading population stream", e);
            }
        }

        @Override
        public java.util.Spliterator<Person> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return java.util.Spliterator.IMMUTABLE | java.util.Spliterator.ORDERED;
        }

        @Override
        public void forEachRemaining(java.util.function.Consumer<? super Person> action) {
            while (tryAdvance(action)) {
            }
        }

        private static Person parsePersonStatic(XMLStreamReader reader) throws XMLStreamException {
            String personId = reader.getAttributeValue(null, "id");
            Person person = new Person(Id.create(personId, Person.class));

            int depth = 1;
            while (depth > 0 && reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tagName = reader.getLocalName();
                    if ("plan".equals(tagName)) {
                        boolean isSelected = "yes".equals(reader.getAttributeValue(null, "selected"));
                        Plan plan = parsePlanStatic(reader);
                        person.addPlan(plan);
                        if (isSelected) {
                            person.setSelectedPlan(plan);
                        }
                    } else {
                        skipElementStatic(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
            return person;
        }

        private static Plan parsePlanStatic(XMLStreamReader reader) throws XMLStreamException {
            Plan plan = new Plan();
            if ("yes".equals(reader.getAttributeValue(null, "selected"))) {
                plan.setSelected(true);
            }

            int depth = 1;
            while (depth > 0 && reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tagName = reader.getLocalName();
                    if ("activity".equals(tagName)) {
                        Activity activity = parseActivityStatic(reader);
                        plan.addPlanElement(activity);
                    } else if ("leg".equals(tagName)) {
                        Leg leg = parseLegStatic(reader);
                        plan.addPlanElement(leg);
                    } else {
                        skipElementStatic(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
            return plan;
        }

        private static Activity parseActivityStatic(XMLStreamReader reader) throws XMLStreamException {
            String facilityStr = reader.getAttributeValue(null, "facility");
            Id<ActivityFacility> facilityId = facilityStr != null ? Id.create(facilityStr, ActivityFacility.class) : null;
            String type = reader.getAttributeValue(null, "type");
            String endTimeStr = reader.getAttributeValue(null, "end_time");
            String xStr = reader.getAttributeValue(null, "x");
            String yStr = reader.getAttributeValue(null, "y");

            double x = xStr != null ? Double.parseDouble(xStr) : 0.0;
            double y = yStr != null ? Double.parseDouble(yStr) : 0.0;

            Activity activity = new Activity(facilityId, type, endTimeStr, x, y);
            skipElementStatic(reader);
            return activity;
        }

        private static Leg parseLegStatic(XMLStreamReader reader) throws XMLStreamException {
            String mode = reader.getAttributeValue(null, "mode");
            Leg leg = new Leg(mode);

            int depth = 1;
            while (depth > 0 && reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tagName = reader.getLocalName();
                    if ("route".equals(tagName)) {
                        Route route = parseRouteStatic(reader);
                        if (route != null) {
                            leg.setRoute(route);
                        }
                    } else {
                        skipElementStatic(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
            return leg;
        }

        private static Route parseRouteStatic(XMLStreamReader reader) throws XMLStreamException {
            String routeType = reader.getAttributeValue(null, "type");
            if ("NetworkRoute".equals(routeType) || "links".equals(routeType)) {
                return parseNetworkRouteStatic(reader);
            } else if ("TransitPassengerRoute".equals(routeType) || "pt".equals(routeType)) {
                return parseTransitPassengerRouteStatic(reader);
            }
            skipElementStatic(reader);
            return null;
        }

        private static NetworkRoute parseNetworkRouteStatic(XMLStreamReader reader) throws XMLStreamException {
            NetworkRoute route = new NetworkRoute();

            String startLink = firstNonBlankStatic(
                    reader.getAttributeValue(null, "start_link"),
                    reader.getAttributeValue(null, "startLink"));
            if (startLink != null) {
                route.setStartLinkId(Id.create(startLink, Link.class));
            }

            String endLink = firstNonBlankStatic(
                    reader.getAttributeValue(null, "end_link"),
                    reader.getAttributeValue(null, "endLink"));
            if (endLink != null) {
                route.setEndLinkId(Id.create(endLink, Link.class));
            }

            String links = null;

            int depth = 1;
            while (depth > 0 && reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tagName = reader.getLocalName();
                    if ("links".equals(tagName)) {
                        links = reader.getElementText();
                    } else {
                        skipElementStatic(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }

            if (links != null && !links.isBlank()) {
                for (String link : links.split("[\\s,]+")) {
                    if (!link.isBlank()) {
                        route.addLinkId(Id.create(link, Link.class));
                    }
                }
            }

            return route;
        }

        private static TransitPassengerRoute parseTransitPassengerRouteStatic(XMLStreamReader reader) throws XMLStreamException {
            TransitPassengerRoute route = new TransitPassengerRoute();

            String accessStopId = firstNonBlankStatic(
                    reader.getAttributeValue(null, "accessStopId"),
                    reader.getAttributeValue(null, "accessStop"));
            if (accessStopId != null) {
                route.setAccessStopId(Id.create(accessStopId, TransitStopFacility.class));
            }

            String egressStopId = firstNonBlankStatic(
                    reader.getAttributeValue(null, "egressStopId"),
                    reader.getAttributeValue(null, "egressStop"));
            if (egressStopId != null) {
                route.setEgressStopId(Id.create(egressStopId, TransitStopFacility.class));
            }

            String lineId = reader.getAttributeValue(null, "lineId");
            if (lineId != null) {
                route.setLineId(Id.create(lineId, TransitLine.class));
            }
            String routeId = reader.getAttributeValue(null, "routeId");
            if (routeId != null) {
                route.setRouteId(Id.create(routeId, TransitRoute.class));
            }
            String departureId = reader.getAttributeValue(null, "departureId");
            if (departureId != null) {
                route.setDepartureId(Id.create(departureId, Departure.class));
            }

            int depth = 1;
            while (depth > 0 && reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tagName = reader.getLocalName();
                    if ("transitRoute".equals(tagName)) {
                        parseTransitRouteDetailsStatic(reader, route);
                    } else {
                        skipElementStatic(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }

            return route;
        }

        private static void parseTransitRouteDetailsStatic(XMLStreamReader reader, TransitPassengerRoute route)
                throws XMLStreamException {
            String lineId = reader.getAttributeValue(null, "line");
            if (lineId != null) {
                route.setLineId(Id.create(lineId, TransitLine.class));
            }
            String routeId = reader.getAttributeValue(null, "route");
            if (routeId != null) {
                route.setRouteId(Id.create(routeId, TransitRoute.class));
            }
            String departureId = reader.getAttributeValue(null, "departure");
            if (departureId != null) {
                route.setDepartureId(Id.create(departureId, Departure.class));
            }

            int depth = 1;
            while (depth > 0 && reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tagName = reader.getLocalName();
                    if ("accessStop".equals(tagName)) {
                        String accessStop = reader.getAttributeValue(null, "stop");
                        if (accessStop != null && route.getAccessStopId() == null) {
                            route.setAccessStopId(Id.create(accessStop, TransitStopFacility.class));
                        }
                        skipElementStatic(reader);
                    } else if ("egressStop".equals(tagName)) {
                        String egressStop = reader.getAttributeValue(null, "stop");
                        if (egressStop != null && route.getEgressStopId() == null) {
                            route.setEgressStopId(Id.create(egressStop, TransitStopFacility.class));
                        }
                        skipElementStatic(reader);
                    } else {
                        skipElementStatic(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
        }

        private static String firstNonBlankStatic(String... values) {
            for (String v : values) {
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
            return null;
        }

        private static void skipElementStatic(XMLStreamReader reader) throws XMLStreamException {
            int depth = 1;
            while (depth > 0 && reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
        }
    }
}
