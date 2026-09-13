package com.citymodeler.matsim.models.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.LanesXmlReader;
import com.citymodeler.matsim.models.io.NetworkXmlReader;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;

class OsmGtfsBundleRunnerLanesTest {

    @Test
    void bundleWritesOneLaneAssignmentPerNonDeadEndLink(@TempDir Path out) throws Exception {
        Path osm = Path.of("src/test/resources/osm/access-oneway-lanes.osm");
        OsmGtfsBundleRunner.BundleResult result =
                new OsmGtfsBundleRunner().run(osm, null, null, out);

        assertNotNull(result.lanesFile());
        assertTrue(Files.exists(result.lanesFile()));

        Network network = new NetworkXmlReader().read(result.networkFile());
        Set<String> deadEnds = deadEndLinkIds(network);

        assertTrue(result.laneAssignments() == network.getLinks().size() - deadEnds.size(),
                "every non-dead-end link must have exactly one lane assignment");

        Lanes lanes = new LanesXmlReader().read(result.lanesFile());
        assertEquals(result.laneAssignments(), lanes.getLanesToLinkAssignments().size());
        for (Link link : network.getLinks().values()) {
            boolean assigned = lanes.getLanesToLinkAssignments().containsKey(link.getId());
            if (deadEnds.contains(link.getId().toString())) {
                assertFalse(assigned, "dead-end link " + link.getId() + " must have no assignment");
            } else {
                assertTrue(assigned, "non-dead-end link " + link.getId() + " must have an assignment");
            }
        }
    }

    @Test
    void deadEndLinkIsSkippedAndReported(@TempDir Path out) throws Exception {
        Path osm = Path.of("src/test/resources/osm/access-oneway-lanes-deadend.osm");
        OsmGtfsBundleRunner.BundleResult result =
                new OsmGtfsBundleRunner().run(osm, null, null, out);

        Network network = new NetworkXmlReader().read(result.networkFile());
        Set<String> deadEnds = deadEndLinkIds(network);
        assertFalse(deadEnds.isEmpty(), "fixture must contain at least one dead-end link");

        Lanes lanes = new LanesXmlReader().read(result.lanesFile());
        assertEquals(network.getLinks().size() - deadEnds.size(), result.laneAssignments());
        for (String deadEnd : deadEnds) {
            assertFalse(lanes.getLanesToLinkAssignments().containsKey(Id.create(deadEnd, Link.class)),
                    "dead-end link " + deadEnd + " must not be assigned a lane");
        }
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("lane-no-outgoing")
                        || (w.contains(deadEnds.iterator().next()) && w.contains("no lane assignment"))),
                "a lane-no-outgoing diagnostic must be reported for the dead-end link(s): "
                        + result.warnings());
    }

    /** Link ids whose to-node has no outgoing links (a terminal / dead-end link). */
    private static Set<String> deadEndLinkIds(Network network) {
        Set<String> deadEnds = new LinkedHashSet<>();
        for (Link link : network.getLinks().values()) {
            var toNode = network.getNodes().get(link.getToNodeId());
            if (toNode == null || toNode.getOutLinks().isEmpty()) {
                deadEnds.add(link.getId().toString());
            }
        }
        return deadEnds;
    }
}
