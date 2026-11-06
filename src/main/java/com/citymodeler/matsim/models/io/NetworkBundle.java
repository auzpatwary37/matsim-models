package com.citymodeler.matsim.models.io;

import java.nio.file.Path;
import java.util.List;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.validation.ValidationIssue;

/**
 * A network plus the lane block embedded in the same document, with network
 * and embedded-lane diagnostics kept in SEPARATE lists: the application
 * validates the CHOSEN lane source and retains shadowed-source diagnostics,
 * so malformed embedded content can never veto a valid explicit override.
 *
 * @param source         file the bundle was read from
 * @param network        parsed network (semantic defects appear in
 *                     {@code networkIssues}; the bundle is then not
 *                     installation-ready)
 * @param networkIssues  network-domain issues from structural parsing and
 *                     validation; never null
 * @param embeddedLanes  result of the embedded lane block; never null
 */
public record NetworkBundle(Path source, Network network,
        List<ValidationIssue> networkIssues, LaneReadResult embeddedLanes) {

    public NetworkBundle {
        networkIssues = List.copyOf(networkIssues);
    }
}
