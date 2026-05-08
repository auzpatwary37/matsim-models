package com.citymodeler.matsim.models.population;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

class NetworkRouteTest {

    @Test
    void getLinkIds_returnsUnmodifiableView() {
        NetworkRoute route = new NetworkRoute();
        route.setStartLinkId(Id.create("1", Link.class));
        route.setEndLinkId(Id.create("3", Link.class));
        assertThrows(UnsupportedOperationException.class, () -> route.getLinkIds().clear());
    }

    @Test
    void addLinkId_works() {
        NetworkRoute route = new NetworkRoute();
        route.addLinkId(Id.create("l1", Link.class));
        assertEquals(1, route.getLinkIds().size());
        assertEquals("l1", route.getLinkIds().get(0).toString());
    }
}
