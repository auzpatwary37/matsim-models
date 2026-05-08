package com.citymodeler.matsim.models.validation;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;

public final class NetworkValidator {
    private final Network network;
    private final ValidationReport report;

    public NetworkValidator(Network network) {
        this.network = network;
        this.report = new ValidationReport();
    }

    public static ValidationReport validate(Network network) {
        NetworkValidator validator = new NetworkValidator(network);
        validator.validate();
        return validator.report;
    }

    public void validate() {
        validateNodes();
        validateLinks();
        validateLinkConnectivity();
    }

    private void validateNodes() {
        for (var entry : network.getNodes().entrySet()) {
            var node = entry.getValue();
            if (node.getCoord() == null) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "network",
                        "node-missing-coord",
                        "Node " + entry.getKey() + " has no coordinate",
                        entry.getKey().toString(),
                        "Add coordinate to node"));
            }
        }
    }

    private void validateLinks() {
        for (var entry : network.getLinks().entrySet()) {
            var link = entry.getValue();
            String linkId = entry.getKey().toString();

            if (link.getLength() <= 0) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "network",
                        "link-invalid-length",
                        "Link " + linkId + " has non-positive length: " + link.getLength(),
                        linkId,
                        "Set link length to a positive value"));
            }

            if (link.getFreespeed() <= 0) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "network",
                        "link-invalid-freespeed",
                        "Link " + linkId + " has non-positive freespeed: " + link.getFreespeed(),
                        linkId,
                        "Set link freespeed to a positive value"));
            }

            if (link.getNumberOfLanes() <= 0) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "network",
                        "link-invalid-lanes",
                        "Link " + linkId + " has non-positive lanes: " + link.getNumberOfLanes(),
                        linkId,
                        "Set link lanes to a positive integer"));
            }

            if (link.getCapacity() < 0) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "network",
                        "link-negative-capacity",
                        "Link " + linkId + " has negative capacity: " + link.getCapacity(),
                        linkId,
                        "Set link capacity to a non-negative value"));
            }
        }
    }

    private void validateLinkConnectivity() {
        for (var entry : network.getLinks().entrySet()) {
            var link = entry.getValue();
            String linkId = entry.getKey().toString();

            var fromNode = network.getNodes().get(link.getFromNodeId());
            if (fromNode == null) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "network",
                        "link-missing-from-node",
                        "Link " + linkId + " references missing from-node: " + link.getFromNodeId(),
                        linkId,
                        "Ensure from-node exists in network"));
            }

            var toNode = network.getNodes().get(link.getToNodeId());
            if (toNode == null) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "network",
                        "link-missing-to-node",
                        "Link " + linkId + " references missing to-node: " + link.getToNodeId(),
                        linkId,
                        "Ensure to-node exists in network"));
            }
        }
    }

    public ValidationReport getReport() {
        return report;
    }
}