package com.citymodeler.matsim.models.validation;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the topology of a {@link Network} and collects structural issues.
 */
public final class NetworkValidator {

    /**
     * Runs validation rules on the given network.
     *
     * @param network the network to validate; must not be {@code null}
     * @return a {@link ValidationReport} with any issues found (may be empty)
     */
    public ValidationReport validate(Network network) {
        if (network == null) {
            throw new IllegalArgumentException("network is required");
        }

        List<ValidationIssue> issues = new ArrayList<>();

        for (Node node : network.getNodes().values()) {
            if (node.getCoord() == null) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, "network", "missing_coord",
                    "Node has no coordinate", node.getId().toString(),
                    "Set a coordinate for the node"));
            }
        }

        for (Link link : network.getLinks().values()) {
            Node fromNode = link.getFromNode();
            Node toNode = link.getToNode();
            if (fromNode == null || toNode == null) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, "network", "unresolved_nodes",
                    "Link references unresolved nodes; run postProcess()", link.getId().toString(),
                    "Call network.postProcess() after adding links"));
            }
            if (fromNode != null && toNode != null && fromNode.equals(toNode)) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING, "network", "self_loop",
                    "Link is a self-loop (fromNode == toNode)", link.getId().toString(),
                    "Consider removing or rewriting the link"));
            }
            if (link.getLength() <= 0) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING, "network", "nonpositive_length",
                    "Link length must be positive", link.getId().toString(),
                    "Set length > 0"));
            }
            if (link.getCapacity() <= 0) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING, "network", "nonpositive_capacity",
                    "Link capacity must be positive", link.getId().toString(),
                    "Set capacity > 0"));
            }
            if (link.getFreespeed() <= 0) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING, "network", "nonpositive_freespeed",
                    "Link freespeed must be positive", link.getId().toString(),
                    "Set freespeed > 0"));
            }
            if (link.getNumberOfLanes() <= 0) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING, "network", "nonpositive_lanes",
                    "Link lanes must be positive", link.getId().toString(),
                    "Set numberOfLanes > 0"));
            }
            if (link.getAllowedModes() == null || link.getAllowedModes().isEmpty()) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.INFO, "network", "no_modes",
                    "Link has no allowed modes (defaults to car)", link.getId().toString(),
                    "Set allowedModes"));
            }
        }

        for (Node node : network.getNodes().values()) {
            if (node.getInLinks().isEmpty() && node.getOutLinks().isEmpty()) {
                issues.add(new ValidationIssue(
                    ValidationSeverity.INFO, "network", "isolated_node",
                    "Node has no incident links", node.getId().toString(),
                    "Connect the node to the network or remove it"));
            }
        }

        return new ValidationReport(issues);
    }
}
