package com.citymodeler.matsim.models.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

class NetworkValidatorTest {

    private final NetworkValidator validator = new NetworkValidator();

    private Network createValidNetwork() {
        Network network = new Network("test");
        Node n1 = network.createNode("n1", 0, 0);
        Node n2 = network.createNode("n2", 100, 0);
        network.createLink("l1", "n1", "n2", 100, 1000, 13.89, 1, Set.of("car"));
        network.postProcess();
        return network;
    }

    @Test
    void validNetworkHasNoIssues() {
        Network network = createValidNetwork();
        ValidationReport report = validator.validate(network);
        assertTrue(report.isEmpty(), report.toString());
    }

    @Test
    void missingCoordReportsError() {
        Network network = new Network();
        Node n1 = new Node(Id.create("n1", Node.class), null);
        network.addNode(n1);
        network.postProcess();

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasErrors());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("missing_coord")));
    }

    @Test
    void selfLoopReportsWarning() {
        Network network = new Network();
        Node n1 = network.createNode("n1", 0, 0);
        Link link = new Link(Id.createLinkId("l1"), n1.getId(), n1.getId(), 100, 1000, 13.89, 1, Set.of("car"));
        network.addLink(link);
        network.postProcess();

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasWarnings());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("self_loop")));
    }

    @Test
    void nonpositiveLengthReportsWarning() {
        Network network = createValidNetwork();
        Link link = network.getLinks().values().iterator().next();
        link.setLength(-5);

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasWarnings());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("nonpositive_length")));
    }

    @Test
    void unresolvedNodesReportsError() {
        Network network = new Network();
        Node n1 = network.createNode("n1", 0, 0);
        Link link = new Link(Id.createLinkId("l1"), n1.getId(), Id.create("missing", Node.class), 100, 1000, 13.89, 1, Set.of("car"));
        network.addLink(link);
        // postProcess skipped intentionally to leave nodes unresolved

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasErrors());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("unresolved_nodes")));
    }

    @Test
    void isolatedNodeReportsInfo() {
        Network network = new Network();
        network.createNode("n1", 0, 0);
        network.postProcess();

        ValidationReport report = validator.validate(network);
        assertFalse(report.isEmpty());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("isolated_node")));
    }
}
