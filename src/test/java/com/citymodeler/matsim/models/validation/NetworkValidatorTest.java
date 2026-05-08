package com.citymodeler.matsim.models.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

import org.junit.jupiter.api.Test;

class NetworkValidatorTest {

    @Test
    void validNetwork_noIssues() {
        Network network = new Network();
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(0, 0));
        Node n2 = new Node(Id.create("n2", Node.class), new Coord(100, 0));
        network.addNode(n1);
        network.addNode(n2);
        Link l1 = new Link(Id.create("l1", Link.class), Id.create("n1", Node.class),
                Id.create("n2", Node.class), 100, 3600, 13.9, 1);
        network.addLink(l1);
        network.postProcess();

        ValidationReport report = NetworkValidator.validate(network);

        assertFalse(report.hasErrors());
        assertFalse(report.hasWarnings());
    }

    @Test
    void linkMissingFromNode_reportsError() {
        Network network = new Network();
        Node n2 = new Node(Id.create("n2", Node.class), new Coord(100, 0));
        network.addNode(n2);
        Link l1 = new Link(Id.create("l1", Link.class), Id.create("n1", Node.class),
                Id.create("n2", Node.class), 100, 3600, 13.9, 1);
        network.addLink(l1);

        ValidationReport report = NetworkValidator.validate(network);

        assertTrue(report.hasErrors());
        assertEquals(1, report.getErrors().size());
        assertEquals("link-missing-from-node", report.getErrors().get(0).getCode());
        assertEquals("l1", report.getErrors().get(0).getObjectId());
    }

    @Test
    void linkMissingToNode_reportsError() {
        Network network = new Network();
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(0, 0));
        network.addNode(n1);
        Link l1 = new Link(Id.create("l1", Link.class), Id.create("n1", Node.class),
                Id.create("n2", Node.class), 100, 3600, 13.9, 1);
        network.addLink(l1);

        ValidationReport report = NetworkValidator.validate(network);

        assertTrue(report.hasErrors());
        assertEquals(1, report.getErrors().size());
        assertEquals("link-missing-to-node", report.getErrors().get(0).getCode());
    }

    @Test
    void linkNonPositiveLength_reportsError() {
        Network network = new Network();
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(0, 0));
        Node n2 = new Node(Id.create("n2", Node.class), new Coord(100, 0));
        network.addNode(n1);
        network.addNode(n2);
        Link l1 = new Link(Id.create("l1", Link.class), Id.create("n1", Node.class),
                Id.create("n2", Node.class), -1, 3600, 13.9, 1);
        network.addLink(l1);

        ValidationReport report = NetworkValidator.validate(network);

        assertTrue(report.hasErrors());
        assertEquals("link-invalid-length", report.getErrors().get(0).getCode());
    }

    @Test
    void linkNonPositiveFreespeed_reportsError() {
        Network network = new Network();
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(0, 0));
        Node n2 = new Node(Id.create("n2", Node.class), new Coord(100, 0));
        network.addNode(n1);
        network.addNode(n2);
        Link l1 = new Link(Id.create("l1", Link.class), Id.create("n1", Node.class),
                Id.create("n2", Node.class), 100, 0, 13.9, 1);
        network.addLink(l1);

        ValidationReport report = NetworkValidator.validate(network);

        assertTrue(report.hasErrors());
        assertEquals("link-invalid-freespeed", report.getErrors().get(0).getCode());
    }

    @Test
    void linkNegativeCapacity_reportsError() {
        Network network = new Network();
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(0, 0));
        Node n2 = new Node(Id.create("n2", Node.class), new Coord(100, 0));
        network.addNode(n1);
        network.addNode(n2);
        Link l1 = new Link(Id.create("l1", Link.class), Id.create("n1", Node.class),
                Id.create("n2", Node.class), 100, -1, 13.9, 1);
        network.addLink(l1);

        ValidationReport report = NetworkValidator.validate(network);

        assertTrue(report.hasErrors());
        assertEquals("link-negative-capacity", report.getErrors().get(0).getCode());
    }

    @Test
    void nodeMissingCoord_reportsWarning() {
        Network network = new Network();
        Node n1 = new Node(Id.create("n1", Node.class), null);
        network.addNode(n1);

        ValidationReport report = NetworkValidator.validate(network);

        assertFalse(report.hasErrors());
        assertTrue(report.hasWarnings());
        assertEquals("node-missing-coord", report.getWarnings().get(0).getCode());
    }
}