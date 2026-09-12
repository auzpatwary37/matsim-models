package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

import java.util.List;
import java.util.Set;

/**
 * Review regressions for the transit routing layer: distance metric (#3), final-turn restriction
 * checking (#4), multi-link restriction sequences (#5), and explicit route-mode evaluation (#6).
 */
class RoutePathFinderTest {

    private final Network network = new Network();
    private final Set<Id<Link>> none = Set.of();

    private Node node(String id, double x, double y) {
        Node n = new Node(Id.create(id, Node.class), new Coord(x, y));
        network.addNode(n);
        return n;
    }

    private Link link(String id, Node from, Node to, double len, double speed, Set<String> modes) {
        Link l = new Link(Id.create(id, Link.class), from.getId(), to.getId(), len, 900.0, speed, 2.0, modes);
        network.addLink(l);
        return l;
    }

    private Id<Link> id(String s) {
        return Id.create(s, Link.class);
    }

    private RoutePathFinder finder(double maxDistance, String mode) {
        network.postProcess();
        return new RoutePathFinder(network, none, maxDistance, mode);
    }

    /** Review #3: the termination cap and the edge metric are both distance, not travel time. */
    @Test
    void distanceMetricPrefersShortSlowPathOverLongFastPath() {
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 100, 0);
        Node n2 = node("n2", 200, 0);
        Node n3 = node("n3", 250, 0);
        link("F", n0, n1, 100, 10, Set.of("car"));
        link("P1", n1, n2, 120, 2, Set.of("car"));   // short but slow
        link("P2", n1, n2, 1500, 100, Set.of("car")); // long but very fast
        link("D", n2, n3, 10, 10, Set.of("car"));

        List<Id<Link>> path = finder(300.0, "car").findPath(id("F"), id("D"));
        assertNotNull(path, "short path is within the distance cap");
        assertEquals(List.of(id("F"), id("P1"), id("D")), path);
    }

    /** Review #4: the final turn into the destination link is restriction-checked. */
    @Test
    void finalTurnIntoDestinationIsRestrictionChecked() {
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 100, 0);
        Node n2 = node("n2", 200, 0);
        Node n3 = node("n3", 300, 0);
        link("F", n0, n1, 100, 10, Set.of("bus", "car"));
        Link p1 = link("P1", n1, n2, 100, 10, Set.of("bus", "car"));
        p1.getAttributes().putAttribute("disallowedNextLinks", "{\"bus\":[[\"D\"]]}");
        link("D", n2, n3, 100, 10, Set.of("bus", "car"));

        // Bus: the only path requires the restricted final turn P1 -> D, so no path.
        assertNull(finder(1000.0, "bus").findPath(id("F"), id("D")));
        // Car: no restriction, path found.
        assertNotNull(finder(1000.0, "car").findPath(id("F"), id("D")));
    }

    /** Review #5: a restriction that only completes after two links is enforced via link history. */
    @Test
    void multiLinkRestrictionSequenceIsEnforced() {
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 100, 0);
        Node n2 = node("n2", 200, 0);
        Node n3 = node("n3", 300, 0);
        Node n4 = node("n4", 400, 0);
        Node n5 = node("n5", 500, 0);
        link("F", n0, n1, 100, 10, Set.of("car"));
        Link l0 = link("L0", n1, n2, 100, 10, Set.of("car"));
        // After L0, the SEQUENCE A then B is forbidden (individually A and B are fine).
        l0.getAttributes().putAttribute("disallowedNextLinks", "{\"car\":[[\"A\",\"B\"]]}");
        link("A", n2, n3, 100, 10, Set.of("car"));
        link("B", n3, n4, 100, 10, Set.of("car"));
        link("D", n4, n5, 100, 10, Set.of("car"));

        // The only route F->L0->A->B->D triggers the forbidden A,B sequence after L0.
        assertNull(finder(10000.0, "car").findPath(id("F"), id("D")));
    }

    /**
     * Review #5 control: a single-link (not multi-link) restriction is still applied, and a
     * one-link-away restriction does not block a longer sequence.
     */
    @Test
    void singleLinkRestrictionStillApplies() {
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 100, 0);
        Node n2 = node("n2", 200, 0);
        Node n3 = node("n3", 300, 0);
        link("F", n0, n1, 100, 10, Set.of("car"));
        Link l0 = link("L0", n1, n2, 100, 10, Set.of("car"));
        l0.getAttributes().putAttribute("disallowedNextLinks", "{\"car\":[[\"B\"]]}");
        link("A", n2, n3, 100, 10, Set.of("car"));
        link("D", n3, n1, 100, 10, Set.of("car"));
        // B is disallowed right after L0; going L0->A->... is allowed.
        network.postProcess();
        RoutePathFinder f = new RoutePathFinder(network, none, 10000.0, "car");
        assertNotNull(f.findPath(id("F"), id("A")), "A is not the restricted successor of L0");
    }

    /**
     * Review #1: traversal enforces link mode access. The geometrically shortest path runs through a
     * car-only intermediate link, so a bus route must take a longer transit-capable path instead.
     */
    @Test
    void busRouteAvoidsCarOnlyIntermediateLink() {
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 100, 0);
        Node n2 = node("n2", 200, 0);
        Node n3 = node("n3", 300, 0);
        Node n4 = node("n4", 400, 0);
        link("F", n0, n1, 100, 10, Set.of("bus", "car"));
        // Short path via a car-only intermediate link: n1 -C-> n2 -B-> n3.
        link("C", n1, n2, 50, 10, Set.of("car"));
        link("B", n2, n3, 50, 10, Set.of("bus", "car"));
        // Longer, fully bus-compatible detour: n1 -X1-> n4 -X2-> n2, then the shared B -> n3.
        link("X1", n1, n4, 200, 10, Set.of("bus", "car"));
        link("X2", n4, n2, 200, 10, Set.of("bus", "car"));

        // Bus must reach n2 without the car-only C link, then traverse B.
        List<Id<Link>> bus = finder(10000.0, "bus").findPath(id("F"), id("B"));
        assertNotNull(bus, "a bus-compatible path to B exists via X1/X2");
        assertFalse(bus.contains(id("C")), "bus path must not traverse car-only link C");
        assertTrue(bus.contains(id("B")));

        // Car is free to take the short car-only route.
        List<Id<Link>> car = finder(10000.0, "car").findPath(id("F"), id("B"));
        assertNotNull(car);
        assertTrue(car.contains(id("C")), "car takes the shortest path through C");
    }

    /**
     * Review #1 no-compatible-path case: when no mode-compatible path exists, path search returns
     * null so the caller can fall back to an explicit connector/unmapped result.
     */
    @Test
    void noCompatiblePathReturnsNull() {
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 100, 0);
        Node n2 = node("n2", 200, 0);
        Node n3 = node("n3", 300, 0);
        link("F", n0, n1, 100, 10, Set.of("bus", "car"));
        link("C", n1, n2, 100, 10, Set.of("car")); // only link onward is car-only
        link("D", n2, n3, 100, 10, Set.of("car"));

        assertNull(finder(10000.0, "bus").findPath(id("F"), id("D")),
                "no bus-compatible path => null");
        assertNotNull(finder(10000.0, "car").findPath(id("F"), id("D")));
    }

    /** Review #6: the route's own mode decides which restrictions apply, not an arbitrary link mode. */
    @Test
    void routeModeDrivesRestrictionEvaluation() {
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 100, 0);
        Node n2 = node("n2", 200, 0);
        Node n3 = node("n3", 300, 0);
        // Every link allows both bus and car; only the BUS restriction is defined.
        link("F", n0, n1, 100, 10, Set.of("bus", "car"));
        Link p1 = link("P1", n1, n2, 100, 10, Set.of("bus", "car"));
        p1.getAttributes().putAttribute("disallowedNextLinks", "{\"bus\":[[\"P2\"]]}");
        link("P2", n2, n3, 100, 10, Set.of("bus", "car"));

        // A bus route is blocked by its own mode's restriction.
        assertNull(finder(1000.0, "bus").findPath(id("F"), id("P2")));
        // A car route on the identical network is not.
        List<Id<Link>> car = finder(1000.0, "car").findPath(id("F"), id("P2"));
        assertNotNull(car);
        assertEquals(List.of(id("F"), id("P1"), id("P2")), car);
        assertTrue(car.size() == 3);
    }
}
