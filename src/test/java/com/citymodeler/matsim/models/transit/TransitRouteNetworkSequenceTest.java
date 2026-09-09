package com.citymodeler.matsim.models.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

class TransitRouteNetworkSequenceTest {

    @Test
    void networkRouteIsNullUntilSet() {
        assertNull(new TransitRoute(Id.create("r", TransitRoute.class)).getNetworkRoute());
    }

    @Test
    void setAndGetRoundTripsOrderedSequence() {
        TransitRoute route = new TransitRoute(Id.create("r", TransitRoute.class));
        route.setNetworkRoute(List.of(
                Id.create("l1", Link.class),
                Id.create("l2", Link.class),
                Id.create("l3", Link.class)));
        assertEquals(List.of(
                Id.create("l1", Link.class),
                Id.create("l2", Link.class),
                Id.create("l3", Link.class)), route.getNetworkRoute());
    }

    @Test
    void returnedSequenceIsImmutable() {
        TransitRoute route = new TransitRoute(Id.create("r", TransitRoute.class));
        route.setNetworkRoute(List.of(Id.create("l1", Link.class)));
        assertThrows(UnsupportedOperationException.class,
                () -> route.getNetworkRoute().add(Id.create("l2", Link.class)));
    }

    @Test
    void setterStoresDefensiveCopy() {
        TransitRoute route = new TransitRoute(Id.create("r", TransitRoute.class));
        List<Id<Link>> source = new java.util.ArrayList<>(List.of(Id.create("l1", Link.class)));
        route.setNetworkRoute(source);
        source.add(Id.create("l2", Link.class));
        assertEquals(1, route.getNetworkRoute().size());
    }

    @Test
    void setterRejectsEmptySequence() {
        TransitRoute route = new TransitRoute(Id.create("r", TransitRoute.class));
        assertThrows(IllegalArgumentException.class, () -> route.setNetworkRoute(List.of()));
    }

    @Test
    void setterRejectsNullElements() {
        TransitRoute route = new TransitRoute(Id.create("r", TransitRoute.class));
        assertThrows(IllegalArgumentException.class,
                () -> route.setNetworkRoute(Arrays.asList(Id.create("l1", Link.class), null)));
    }

    @Test
    void nullClearsSequence() {
        TransitRoute route = new TransitRoute(Id.create("r", TransitRoute.class));
        route.setNetworkRoute(List.of(Id.create("l1", Link.class)));
        route.setNetworkRoute(null);
        assertNull(route.getNetworkRoute());
    }
}
