package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * Physically meaningful stop-to-link mapping assertions: a stop named after a road must snap to the
 * candidate link carrying that road name, and the chosen candidate must be within the configured
 * candidate radius. These are real numeric checks, not tautologies.
 */
class StopLinkPhysicalAssertionTest {

    @Test
    void stopNamedAfterRoadSnapsToThatNamedLink() {
        Network network = new Network();
        Node a = new Node(Id.create("A", Node.class), new Coord(0, 0));
        Node b = new Node(Id.create("B", Node.class), new Coord(100, 0));
        // The named road runs parallel, 30 m north of the stop.
        Node a2 = new Node(Id.create("A2", Node.class), new Coord(0, 30));
        Node b2 = new Node(Id.create("B2", Node.class), new Coord(100, 30));
        network.addNode(a);
        network.addNode(b);
        network.addNode(a2);
        network.addNode(b2);

        // Closer but unnamed competitor: the stop sits exactly on it.
        Link unnamed = new Link(Id.create("unnamed", Link.class), a.getId(), b.getId(),
                100.0, 900.0, 13.9, 2.0, Set.of("car", "pt"));
        network.addLink(unnamed);
        Link named = new Link(Id.create("named", Link.class), a2.getId(), b2.getId(),
                100.0, 900.0, 13.9, 2.0, Set.of("car", "pt"));
        named.getAttributes().putAttribute("osm:name", "Rue de Hamm");
        network.addLink(named);
        network.postProcess();

        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility stop = new TransitStopFacility(
                Id.create("hamm", TransitStopFacility.class), new Coord(50, 0), false);
        stop.setName("Hamm");
        schedule.addStopFacility(stop);

        TransitMappingConfig config = TransitMappingConfig.defaults();
        LinkSpatialIndex index = new LinkSpatialIndex(network, 100.0);
        List<StopCandidate> candidates =
                new StopCandidateScorer(config, index, network).score(stop, "pt");

        assertFalse(candidates.isEmpty(), "expected at least one candidate within radius");
        StopCandidate chosen = candidates.get(0);
        Link chosenLink = network.getLinks().get(chosen.linkId());

        // The named road must win on name similarity even though the unnamed link is closer.
        assertEquals(Id.create("named", Link.class), chosen.linkId(),
                "stop 'Hamm' must snap to 'Rue de Hamm', not the closer unnamed link");

        double similarity = StopCandidateScorer.nameSimilarity(stop.getName(), chosenLink);
        assertTrue(similarity > 0.5,
                "nameSimilarity('Hamm', chosen) must exceed 0.5 but was " + similarity);
        assertTrue(chosen.distanceMeters() < config.maxLinkCandidateDistanceMeters(),
                "chosen distance " + chosen.distanceMeters()
                        + " must be within maxLinkCandidateDistanceMeters "
                        + config.maxLinkCandidateDistanceMeters());
    }
}
