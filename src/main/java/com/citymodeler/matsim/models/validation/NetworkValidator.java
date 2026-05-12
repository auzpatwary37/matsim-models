package com.citymodeler.matsim.models.validation;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

import java.util.Objects;

/**
 * Validates the topology of a {@link Network} and collects structural issues.
 * Supports both the older stateful pattern ({@code new NetworkValidator(network)}
 * + {@link #validate()} + {@link #getReport()}) and the newer stateless pattern
 * ({@link #validate(Network)} or {@link #validate(Network)} after a no-arg
 * constructor).
 */
public final class NetworkValidator {
    private final Network network;
    private final ValidationReport report;

    public NetworkValidator() {
        this.network = null;
        this.report = null;
    }

    public NetworkValidator(Network network) {
        this.network = Objects.requireNonNull(network, "network");
        this.report = new ValidationReport();
    }

    /**
     * Convenience method that validates the given network and returns a report.
     *
     * @param network the network to validate; must not be {@code null}
     * @return a {@link ValidationReport} with any issues found (may be empty)
     */
    public static ValidationReport validate(Network network) {
        NetworkValidator validator = new NetworkValidator(network);
        validator.validate();
        return validator.getReport();
    }

    /**
     * Validates the network supplied to the constructor.
     *
     * @throws IllegalStateException if this validator was constructed without a network
     */
    public void validate() {
        if (network == null) {
            throw new IllegalStateException("No network set. Use NetworkValidator(Network) or validate(Network).");
        }
        validateNodes();
        validateLinks();
        validateLinkConnectivity();
        validateIsolatedNodes();
    }

    /**
     * Returns the report populated by the last {@link #validate()} call.
     *
     * @throws IllegalStateException if this validator was constructed without a network
     */
    public ValidationReport getReport() {
        if (report == null) {
            throw new IllegalStateException("No network set. Use NetworkValidator(Network) or validate(Network).");
        }
        return report;
    }

    private void validateNodes() {
        for (Node node : network.getNodes().values()) {
            if (node.getCoord() == null) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR, "network", "missing_coord",
                        "Node " + node.getId() + " has no coordinate",
                        node.getId().toString(),
                        "Add coordinate to node"));
            }
        }
    }

    private void validateLinks() {
        for (Link link : network.getLinks().values()) {
            if (link.getLength() <= 0) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR, "network", "nonpositive_length",
                        "Link " + link.getId() + " has non-positive length: " + link.getLength(),
                        link.getId().toString(),
                        "Set link length to a positive value"));
            }
            if (link.getFreespeed() <= 0) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR, "network", "nonpositive_freespeed",
                        "Link " + link.getId() + " has non-positive freespeed: " + link.getFreespeed(),
                        link.getId().toString(),
                        "Set link freespeed to a positive value"));
            }
            if (link.getCapacity() <= 0) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR, "network", "nonpositive_capacity",
                        "Link " + link.getId() + " has non-positive capacity: " + link.getCapacity(),
                        link.getId().toString(),
                        "Set link capacity to a positive value"));
            }
            if (link.getNumberOfLanes() <= 0) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR, "network", "nonpositive_lanes",
                        "Link " + link.getId() + " has non-positive lanes: " + link.getNumberOfLanes(),
                        link.getId().toString(),
                        "Set link lanes to a positive integer"));
            }
        }
    }

    private void validateLinkConnectivity() {
        for (Link link : network.getLinks().values()) {
            Node fromNodeById = network.getNodes().get(link.getFromNodeId());
            Node toNodeById = network.getNodes().get(link.getToNodeId());

            boolean unresolved = false;

            if (fromNodeById == null) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR, "network", "link-missing-from-node",
                        "Link " + link.getId() + " references missing from-node: " + link.getFromNodeId(),
                        link.getId().toString(),
                        "Ensure from-node exists in network"));
            } else if (link.getFromNode() == null) {
                unresolved = true;
            }

            if (toNodeById == null) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR, "network", "link-missing-to-node",
                        "Link " + link.getId() + " references missing to-node: " + link.getToNodeId(),
                        link.getId().toString(),
                        "Ensure to-node exists in network"));
            } else if (link.getToNode() == null) {
                unresolved = true;
            }

            if (unresolved) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR, "network", "unresolved_nodes",
                        "Link references unresolved nodes; run postProcess()",
                        link.getId().toString(),
                        "Call network.postProcess() after adding links"));
            }

            if (fromNodeById != null && toNodeById != null && fromNodeById.equals(toNodeById)) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.WARNING, "network", "self_loop",
                        "Link " + link.getId() + " is a self-loop (fromNode == toNode)",
                        link.getId().toString(),
                        "Consider removing or rewriting the link"));
            }
        }
    }

    private void validateIsolatedNodes() {
        for (Node node : network.getNodes().values()) {
            if (node.getInLinks().isEmpty() && node.getOutLinks().isEmpty()) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.INFO, "network", "isolated_node",
                        "Node " + node.getId() + " has no incident links",
                        node.getId().toString(),
                        "Connect the node to the network or remove it"));
            }
        }
    }
}
