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
    void staticValidateConvenienceApiStillWorks() {
        Network network = createValidNetwork();
        ValidationReport report = NetworkValidator.validate(network);
        assertTrue(report.isEmpty(), report.toString());
    }

    @Test
    void instanceValidateWithConstructorNetworkStillWorks() {
        Network network = createValidNetwork();
        NetworkValidator validator = new NetworkValidator(network);
        validator.validate();
        assertFalse(validator.getReport().hasErrors());
    }

    @Test
    void missingFromNodeByIdReportsErrorBeforePostProcess() {
        Network network = new Network();
        Node toNode = network.createNode("n2", 0, 0);
        Link link = new Link(Id.createLinkId("l1"), Id.create("missing", Node.class), toNode.getId(), 100, 1000, 13.89, 1, Set.of("car"));
        network.addLink(link);
        // Do NOT call postProcess — we want to validate raw endpoint IDs

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasErrors());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("link-missing-from-node")));
    }

    @Test
    void missingToNodeByIdReportsErrorBeforePostProcess() {
        Network network = new Network();
        Node n1 = network.createNode("n1", 0, 0);
        Link link = new Link(Id.createLinkId("l1"), n1.getId(), Id.create("n2", Node.class), 100, 1000, 13.89, 1, Set.of("car"));
        network.addLink(link);
        // Missing "n2" in network; do NOT call postProcess

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasErrors());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("link-missing-to-node")));
    }

    @Test
    void nonpositiveLengthReportsError() {
        Network network = createValidNetwork();
        Link link = network.getLinks().values().iterator().next();
        link.setLength(-5);

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasErrors());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("nonpositive_length")));
    }

    @Test
    void nonpositiveFreespeedReportsError() {
        Network network = createValidNetwork();
        Link link = network.getLinks().values().iterator().next();
        link.setFreespeed(-1);

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasErrors());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("nonpositive_freespeed")));
    }

    @Test
    void nonpositiveCapacityReportsError() {
        Network network = createValidNetwork();
        Link link = network.getLinks().values().iterator().next();
        link.setCapacity(-1);

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasErrors());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("nonpositive_capacity")));
    }

    @Test
    void nonpositiveLanesReportsError() {
        Network network = createValidNetwork();
        Link link = network.getLinks().values().iterator().next();
        link.setNumberOfLanes(-1);

        ValidationReport report = validator.validate(network);
        assertTrue(report.hasErrors());
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("nonpositive_lanes")));
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

    private Network createSelfLoopNetwork() {
        Network network = new Network();
        Node n1 = network.createNode("n1", 0, 0);
        network.createLink("pt_self_loop", "n1", "n1", 100, 1000, 13.89, 1, Set.of("pt"));
        network.createLink("road_self_loop", "n1", "n1", 100, 1000, 13.89, 1, Set.of("car"));
        network.postProcess();
        return network;
    }

    @Test
    void allowSelfLoopPrefixDowngradesMatchingSelfLoopsToInfo() {
        Network network = createSelfLoopNetwork();

        ValidationReport defaultReport = validator.validate(network);
        assertEquals(2, defaultReport.getIssues().stream()
                .filter(i -> i.getCode().equals("self_loop")).count());

        ValidationReport report = validator.validate(network,
                NetworkValidatorOptions.defaults().allowSelfLoopPrefix("pt_"));
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("self_loop")
                        && i.getObjectId().equals("pt_self_loop") && i.isInfo()),
                String.valueOf(report.getIssues()));
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("self_loop")
                        && i.getObjectId().equals("road_self_loop") && i.isWarning()),
                String.valueOf(report.getIssues()));
    }

    @Test
    void maxLinkLengthFlagsOverlongLinks() {
        Network network = createValidNetwork();

        ValidationReport defaultReport = validator.validate(network);
        assertFalse(defaultReport.getIssues().stream().anyMatch(i -> i.getCode().equals("link_too_long")));

        ValidationReport report = validator.validate(network,
                NetworkValidatorOptions.defaults().maxLinkLengthMeters(90.0));
        assertTrue(report.getIssues().stream().anyMatch(i -> i.getCode().equals("link_too_long")
                && i.getObjectId().equals("l1")), String.valueOf(report.getIssues()));
    }

    @Test
    void requireModesFlagsMissingModeCoverage() {
        Network network = createValidNetwork();

        ValidationReport carReport = validator.validate(network,
                NetworkValidatorOptions.defaults().requireModes(Set.of("car")));
        assertFalse(carReport.getIssues().stream().anyMatch(i -> i.getCode().equals("missing_mode")));

        ValidationReport busReport = validator.validate(network,
                NetworkValidatorOptions.defaults().requireModes(Set.of("bus")));
        assertTrue(busReport.getIssues().stream().anyMatch(i -> i.getCode().equals("missing_mode")
                && i.getMessage().contains("bus")), String.valueOf(busReport.getIssues()));
    }
}
