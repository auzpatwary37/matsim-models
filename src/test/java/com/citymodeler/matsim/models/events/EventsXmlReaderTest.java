package com.citymodeler.matsim.models.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.io.EventsXmlReader;
import com.citymodeler.matsim.models.io.MatsimParseException;

class EventsXmlReaderTest {

    @Test
    void callbackReadsTypedAndGenericEvents() {
        List<MatsimEvent> events = new ArrayList<>();

        new EventsXmlReader().read(fixturePath(), events::add);

        assertEquals(9, events.size());

        ActivityEndEvent actEnd = assertInstanceOf(ActivityEndEvent.class, events.get(0));
        assertEquals(0.0, actEnd.getTime());
        assertEquals("p1", actEnd.getPersonId());
        assertEquals("l1", actEnd.getLinkId());
        assertEquals("f1", actEnd.getFacilityId());
        assertEquals("home", actEnd.getActivityType());

        DepartureEvent departure = assertInstanceOf(DepartureEvent.class, events.get(1));
        assertEquals("car", departure.getLegMode());

        LinkEnterEvent linkEnter = assertInstanceOf(LinkEnterEvent.class, events.get(2));
        assertEquals("veh1", linkEnter.getVehicleId());

        LinkLeaveEvent linkLeave = assertInstanceOf(LinkLeaveEvent.class, events.get(3));
        assertEquals("l1", linkLeave.getLinkId());

        PersonEntersVehicleEvent entersVehicle = assertInstanceOf(PersonEntersVehicleEvent.class, events.get(4));
        assertEquals("p1", entersVehicle.getPersonId());
        assertEquals("veh1", entersVehicle.getVehicleId());

        PersonLeavesVehicleEvent leavesVehicle = assertInstanceOf(PersonLeavesVehicleEvent.class, events.get(5));
        assertEquals("veh1", leavesVehicle.getVehicleId());

        ArrivalEvent arrival = assertInstanceOf(ArrivalEvent.class, events.get(6));
        assertEquals("car", arrival.getLegMode());

        ActivityStartEvent actStart = assertInstanceOf(ActivityStartEvent.class, events.get(7));
        assertEquals("work", actStart.getActivityType());

        GenericEvent custom = assertInstanceOf(GenericEvent.class, events.get(8));
        assertEquals("customEvent", custom.getType());
        assertEquals("kept", custom.getAttributes().get("customField"));
    }

    @Test
    void streamReadsEventsIncrementally() {
        EventsXmlReader reader = new EventsXmlReader();

        try (var stream = reader.stream(fixturePath())) {
            List<MatsimEvent> events = stream.toList();

            assertEquals(9, events.size());
            assertInstanceOf(ActivityEndEvent.class, events.get(0));
            assertInstanceOf(GenericEvent.class, events.get(8));
        }
    }

    @Test
    void typedHandlerDispatchesSpecificCallbacks() {
        CountingEventHandler handler = new CountingEventHandler();

        new EventsXmlReader().read(fixturePath(), handler);

        assertEquals(1, handler.activityStarts);
        assertEquals(1, handler.activityEnds);
        assertEquals(1, handler.departures);
        assertEquals(1, handler.arrivals);
        assertEquals(1, handler.linkEnters);
        assertEquals(1, handler.linkLeaves);
        assertEquals(1, handler.personEntersVehicle);
        assertEquals(1, handler.personLeavesVehicle);
        assertEquals(1, handler.generic);
        assertEquals(9, handler.all);
    }

    @Test
    void malformedXmlThrowsParseException() {
        EventsXmlReader reader = new EventsXmlReader();

        assertThrows(MatsimParseException.class, () -> reader.readString("<events><event></events>", event -> { }));
    }

    private Path fixturePath() {
        return Path.of("src/test/resources/fixtures/events.xml");
    }

    private static final class CountingEventHandler implements MatsimEventHandler {
        private int all;
        private int activityStarts;
        private int activityEnds;
        private int departures;
        private int arrivals;
        private int linkEnters;
        private int linkLeaves;
        private int personEntersVehicle;
        private int personLeavesVehicle;
        private int generic;

        @Override
        public void handle(MatsimEvent event) {
            all++;
        }

        @Override
        public void handleActivityStart(ActivityStartEvent event) {
            activityStarts++;
            MatsimEventHandler.super.handleActivityStart(event);
        }

        @Override
        public void handleActivityEnd(ActivityEndEvent event) {
            activityEnds++;
            MatsimEventHandler.super.handleActivityEnd(event);
        }

        @Override
        public void handleDeparture(DepartureEvent event) {
            departures++;
            MatsimEventHandler.super.handleDeparture(event);
        }

        @Override
        public void handleArrival(ArrivalEvent event) {
            arrivals++;
            MatsimEventHandler.super.handleArrival(event);
        }

        @Override
        public void handleLinkEnter(LinkEnterEvent event) {
            linkEnters++;
            MatsimEventHandler.super.handleLinkEnter(event);
        }

        @Override
        public void handleLinkLeave(LinkLeaveEvent event) {
            linkLeaves++;
            MatsimEventHandler.super.handleLinkLeave(event);
        }

        @Override
        public void handlePersonEntersVehicle(PersonEntersVehicleEvent event) {
            personEntersVehicle++;
            MatsimEventHandler.super.handlePersonEntersVehicle(event);
        }

        @Override
        public void handlePersonLeavesVehicle(PersonLeavesVehicleEvent event) {
            personLeavesVehicle++;
            MatsimEventHandler.super.handlePersonLeavesVehicle(event);
        }

        @Override
        public void handleGeneric(GenericEvent event) {
            generic++;
            MatsimEventHandler.super.handleGeneric(event);
        }
    }
}
