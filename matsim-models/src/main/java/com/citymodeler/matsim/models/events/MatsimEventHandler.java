package com.citymodeler.matsim.models.events;

public interface MatsimEventHandler {
    void handle(MatsimEvent event);

    default void handleActivityStart(ActivityStartEvent event) {
        handle(event);
    }

    default void handleActivityEnd(ActivityEndEvent event) {
        handle(event);
    }

    default void handleDeparture(DepartureEvent event) {
        handle(event);
    }

    default void handleArrival(ArrivalEvent event) {
        handle(event);
    }

    default void handleLinkEnter(LinkEnterEvent event) {
        handle(event);
    }

    default void handleLinkLeave(LinkLeaveEvent event) {
        handle(event);
    }

    default void handlePersonEntersVehicle(PersonEntersVehicleEvent event) {
        handle(event);
    }

    default void handlePersonLeavesVehicle(PersonLeavesVehicleEvent event) {
        handle(event);
    }

    default void handleGeneric(GenericEvent event) {
        handle(event);
    }
}
