package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportIssue;

/** Mode-aware strongly-connected cleaning and quarantine. */
final class OsmNetworkCleanerTest {

    private static Network base() {
        Network n = new Network("clean-test");
        for (String id : List.of("A", "B", "C", "X", "Y", "P", "Q")) {
            n.createNode("osm_node_" + id, 0, 0);
        }
        // Strongly connected car triangle A->B->C->A.
        n.createLink("ab", "osm_node_A", "osm_node_B", 10, 600, 10, 1, Set.of("car"));
        n.createLink("ba", "osm_node_B", "osm_node_A", 10, 600, 10, 1, Set.of("car"));
        n.createLink("bc", "osm_node_B", "osm_node_C", 10, 600, 10, 1, Set.of("car"));
        n.createLink("ca", "osm_node_C", "osm_node_A", 10, 600, 10, 1, Set.of("car"));
        return n;
    }

    private static Set<String> surviving(Network n) {
        Set<String> ids = new TreeSet<>();
        n.getLinks().keySet().forEach(id -> ids.add(id.toString()));
        return ids;
    }

    @Test
    void removesOneWaySinkAndQuarantinesIt() {
        Network n = base();
        n.createLink("ax", "osm_node_A", "osm_node_X", 10, 600, 10, 1, Set.of("car"));
        n.postProcess();

        OsmNetworkCleaner.CleanResult r = OsmNetworkCleaner.clean(n, Set.of("car"));

        assertFalse(surviving(n).contains("ax"), "one-way sink has no return path and must be removed");
        assertEquals(Set.of("ab", "ba", "bc", "ca"), surviving(n));
        assertTrue(r.quarantinedComponents().stream().flatMap(List::stream).anyMatch("ax"::equals));
        assertTrue(hasCode(r, "quarantined-component"));
    }

    @Test
    void removesDisconnectedIslandAndQuarantinesIt() {
        Network n = base();
        // A self-contained two-link island Y<->P (smaller SCC).
        n.createLink("yp", "osm_node_Y", "osm_node_P", 10, 600, 10, 1, Set.of("car"));
        n.createLink("py", "osm_node_P", "osm_node_Y", 10, 600, 10, 1, Set.of("car"));
        n.postProcess();

        OsmNetworkCleaner.CleanResult r = OsmNetworkCleaner.clean(n, Set.of("car"));

        assertEquals(Set.of("ab", "ba", "bc", "ca"), surviving(n));
        assertFalse(n.getNodes().keySet().stream().anyMatch(id -> id.toString().contains("_Y")));
        assertEquals(1, r.quarantinedComponents().size());
        assertEquals(Set.of("py", "yp"), new TreeSet<>(r.quarantinedComponents().get(0)));
    }

    @Test
    void transitIslandSurvivesWhenItsModeIsNotRoutableCleaned() {
        Network n = base();
        // Bus-only island: not in the car routable set and bus is not configured routable here.
        n.createLink("yq", "osm_node_Y", "osm_node_Q", 10, 600, 10, 1, Set.of("bus"));
        n.createLink("qy", "osm_node_Q", "osm_node_Y", 10, 600, 10, 1, Set.of("bus"));
        n.postProcess();

        OsmNetworkCleaner.clean(n, Set.of("car"));

        assertTrue(surviving(n).contains("yq"), "non-routable-mode links keep sinks/sources");
        assertTrue(surviving(n).contains("qy"));
    }

    @Test
    void zeroLengthLinksAreRemovedExceptArtificialConnectors() {
        Network n = base();
        n.createLink("zero", "osm_node_A", "osm_node_X", 0.0, 600, 10, 1, Set.of("car"));
        // Artificial connector inside the strongly connected component, zero length -> exempt.
        n.createLink("pt_ai_1", "osm_node_A", "osm_node_B", 0.0, 600, 10, 1, Set.of("car"));
        n.postProcess();

        OsmNetworkCleaner.CleanResult r = OsmNetworkCleaner.clean(n, Set.of("car"));

        assertFalse(surviving(n).contains("zero"), "zero-length link must be removed");
        assertTrue(surviving(n).contains("pt_ai_1"), "artificial connector is exempt");
        assertTrue(hasCode(r, "removed-invalid-link"));
    }

    @Test
    void connectedNetworkIsUnchangedNoOp() {
        Network n = base();
        // Remove the unused nodes so the base fixture is exactly the connected component.
        for (String id : List.of("X", "Y", "P", "Q")) {
            n.removeNode(com.citymodeler.matsim.models.api.Id.create("osm_node_" + id,
                    com.citymodeler.matsim.models.network.Node.class));
        }
        n.postProcess();

        OsmNetworkCleaner.CleanResult r = OsmNetworkCleaner.clean(n, Set.of("car"));

        assertEquals(0, r.removedLinks());
        assertEquals(0, r.removedNodes());
        assertEquals(Set.of("ab", "ba", "bc", "ca"), surviving(n));
        assertTrue(r.quarantinedComponents().isEmpty());
    }

    private static boolean hasCode(OsmNetworkCleaner.CleanResult r, String code) {
        return r.issues().stream().map(OsmImportIssue::code).anyMatch(code::equals);
    }
}
