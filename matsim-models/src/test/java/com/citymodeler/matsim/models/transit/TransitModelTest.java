package com.citymodeler.matsim.models.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;
import com.citymodeler.matsim.models.facilities.ActivityOption;

class TransitModelTest {
    @Test
    void activityFacilitiesStoresFacilityAndActivityOptionByType() {
        ActivityFacilities facilities = new ActivityFacilities("sample");
        ActivityFacility facility = new ActivityFacility(Id.create("facility1", ActivityFacility.class), new Coord(10, 20));
        ActivityOption option = new ActivityOption("work");
        option.setCapacity(50);

        facility.addActivityOption(option);
        facilities.addFacility(facility);

        assertEquals("sample", facilities.getName());
        assertSame(facility, facilities.getFacilities().get(facility.getId()));
        assertSame(option, facility.getActivityOptions().get("work"));
        assertEquals(50, option.getCapacity());
        assertEquals(0.0, new ActivityOption("home").getCapacity());
    }

    @Test
    void transitScheduleStoresStopsLinesRoutesAndDepartures() {
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop = new TransitStopFacility(Id.create("stop1", TransitStopFacility.class), new Coord(1, 2), false);
        TransitLine line = new TransitLine(Id.create("line1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route1", TransitRoute.class));
        Departure departure = new Departure(Id.create("dep1", Departure.class), 3600);

        route.addStop(new TransitRouteStop(stop.getId(), 0, 30, true));
        route.addDeparture(departure);
        line.addRoute(route);
        schedule.addStopFacility(stop);
        schedule.addTransitLine(line);

        assertSame(stop, schedule.getFacilities().get(stop.getId()));
        assertSame(line, schedule.getTransitLines().get(line.getId()));
        assertSame(route, line.getRoutes().get(route.getId()));
        assertSame(departure, route.getDepartures().get(departure.getId()));
        assertEquals(3600, departure.getDepartureTime());
    }

    @Test
    void postProcessResolvesTransitRouteStopFacility() {
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop = new TransitStopFacility(Id.create("stop1", TransitStopFacility.class), new Coord(1, 2), false);
        TransitLine line = new TransitLine(Id.create("line1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route1", TransitRoute.class));
        TransitRouteStop routeStop = new TransitRouteStop(stop.getId(), 5, 10, false);

        route.addStop(routeStop);
        line.addRoute(route);
        schedule.addStopFacility(stop);
        schedule.addTransitLine(line);
        schedule.postProcess();

        assertSame(stop, routeStop.getStopFacility());
        assertEquals(stop.getId(), routeStop.getStopFacilityId());
        assertEquals(5, routeStop.getArrivalOffset());
        assertEquals(10, routeStop.getDepartureOffset());
        assertEquals(false, routeStop.isAwaitDeparture());
    }

    @Test
    void postProcessThrowsWhenRouteStopReferencesMissingFacility() {
        TransitSchedule schedule = new TransitSchedule();
        Id<TransitStopFacility> missingStopId = Id.create("missingStop", TransitStopFacility.class);
        TransitLine line = new TransitLine(Id.create("line1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route1", TransitRoute.class));

        route.addStop(new TransitRouteStop(missingStopId, 0, 0, false));
        line.addRoute(route);
        schedule.addTransitLine(line);

        IllegalStateException exception = assertThrows(IllegalStateException.class, schedule::postProcess);
        assertTrue(exception.getMessage().contains("missingStop"));
    }

    @Test
    void postProcessClearsStaleResolvedStopFacilityBeforeMissingReferenceFailure() {
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop = new TransitStopFacility(Id.create("stop1", TransitStopFacility.class), new Coord(1, 2), false);
        TransitLine line = new TransitLine(Id.create("line1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route1", TransitRoute.class));
        TransitRouteStop routeStop = new TransitRouteStop(stop.getId(), 0, 0, false);

        route.addStop(routeStop);
        line.addRoute(route);
        schedule.addStopFacility(stop);
        schedule.addTransitLine(line);
        schedule.postProcess();

        assertSame(stop, routeStop.getStopFacility());

        routeStop.setStopFacilityId(Id.create("missingStop", TransitStopFacility.class));
        IllegalStateException exception = assertThrows(IllegalStateException.class, schedule::postProcess);

        assertTrue(exception.getMessage().contains("missingStop"));
        assertNull(routeStop.getStopFacility());
    }

    @Test
    void transitStopFacilityReturnsConfiguredBlockingLane() {
        TransitStopFacility blockingStop = new TransitStopFacility(Id.create("stop1", TransitStopFacility.class), new Coord(1, 2), true);
        TransitStopFacility nonBlockingStop = new TransitStopFacility(Id.create("stop2", TransitStopFacility.class), new Coord(3, 4), false);

        assertTrue(blockingStop.isBlockingLane());
        assertTrue(blockingStop.getIsBlockingLane());
        assertEquals(false, nonBlockingStop.isBlockingLane());
        assertEquals(false, nonBlockingStop.getIsBlockingLane());
    }

    @Test
    void identityModelClassesDoNotExposeIdSetters() {
        assertThrows(NoSuchMethodException.class, () -> ActivityFacility.class.getMethod("setId", Id.class));
        assertThrows(NoSuchMethodException.class, () -> TransitStopFacility.class.getMethod("setId", Id.class));
        assertThrows(NoSuchMethodException.class, () -> TransitLine.class.getMethod("setId", Id.class));
        assertThrows(NoSuchMethodException.class, () -> TransitRoute.class.getMethod("setId", Id.class));
        assertThrows(NoSuchMethodException.class, () -> Departure.class.getMethod("setId", Id.class));
    }
}
