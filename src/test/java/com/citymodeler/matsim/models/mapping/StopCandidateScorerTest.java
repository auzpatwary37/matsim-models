package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

/**
 * Review #7: a generic {@code pt} route request must not be accepted on a car-only street. The
 * scorer must reject transit-incapable links and fall through to a transit-capable one.
 */
class StopCandidateScorerTest {

    private static final double LEN = 900.0;
    private static final double SPEED = 13.9;

    @Test
    void ptRequestRejectsCarOnlyNearestLink() {
        Network network = new Network();
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 10, 0);
        Node n2 = node("n2", 90, 0);
        network.addNode(n0);
        network.addNode(n1);
        network.addNode(n2);
        // Car-only street immediately next to the stop.
        network.addLink(link("car", n0, n1, 10, Set.of("car")));
        // A transit-capable street a little further away.
        network.addLink(link("bus", n1, n2, 80, Set.of("car", "bus")));
        network.postProcess();

        StopCandidateScorer scorer = scorer(network);
        TransitStopFacility stop = stopAt(0, 0);

        List<StopCandidate> candidates = scorer.score(stop, "pt");
        assertFalse(candidates.stream().anyMatch(c -> c.linkId().toString().equals("car")),
                "car-only link must not satisfy a pt request");
        assertEquals(1, candidates.size());
        assertEquals("bus", candidates.get(0).linkId().toString());
    }

    @Test
    void ptRequestAcceptsPtCapableLink() {
        Network network = new Network();
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 80, 0);
        network.addNode(n0);
        network.addNode(n1);
        network.addLink(link("generic_pt", n0, n1, 80, Set.of("pt")));
        network.postProcess();

        List<StopCandidate> candidates = scorer(network).score(stopAt(0, 0), "pt");
        assertEquals(1, candidates.size());
        assertEquals("generic_pt", candidates.get(0).linkId().toString());
    }

    @Test
    void specificBusRequestStillRejectsCarOnlyLink() {
        Network network = new Network();
        Node n0 = node("n0", 0, 0);
        Node n1 = node("n1", 10, 0);
        network.addNode(n0);
        network.addNode(n1);
        network.addLink(link("car", n0, n1, 10, Set.of("car")));
        network.postProcess();

        List<StopCandidate> candidates = scorer(network).score(stopAt(0, 0), "bus");
        assertTrue(candidates.isEmpty(), "a car-only link must not satisfy a bus request");
    }

    private StopCandidateScorer scorer(Network network) {
        return new StopCandidateScorer(TransitMappingConfig.defaults(),
                new LinkSpatialIndex(network, 500.0), network);
    }

    private Node node(String id, double x, double y) {
        return new Node(Id.create(id, Node.class), new Coord(x, y));
    }

    private Link link(String id, Node from, Node to, double len, Set<String> modes) {
        return new Link(Id.create(id, Link.class), from.getId(), to.getId(), len, LEN, SPEED, 2.0, modes);
    }

    private TransitStopFacility stopAt(double x, double y) {
        return new TransitStopFacility(Id.create("s", TransitStopFacility.class), new Coord(x, y), false);
    }
}
