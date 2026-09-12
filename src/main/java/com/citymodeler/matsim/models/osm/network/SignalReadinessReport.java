package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;

/** Diagnostic summary of how signal-ready a simplified network is. Pure data. */
public final class SignalReadinessReport {

    private final int candidateSignalizedJunctions;
    private final int confirmedSignalizedJunctions;
    private final int preservedSignalNodes;
    private final int collapsedGeometryNodes;
    private final int preservedSemanticNodes;
    private final int highDegreeNonSignalizedIntersections;
    private final int movementsWithoutLaneInfo;
    private final List<OsmImportIssue> issues;

    public SignalReadinessReport(
            int candidateSignalizedJunctions,
            int confirmedSignalizedJunctions,
            int preservedSignalNodes,
            int collapsedGeometryNodes,
            int preservedSemanticNodes,
            int highDegreeNonSignalizedIntersections,
            int movementsWithoutLaneInfo,
            List<OsmImportIssue> issues) {
        this.candidateSignalizedJunctions = candidateSignalizedJunctions;
        this.confirmedSignalizedJunctions = confirmedSignalizedJunctions;
        this.preservedSignalNodes = preservedSignalNodes;
        this.collapsedGeometryNodes = collapsedGeometryNodes;
        this.preservedSemanticNodes = preservedSemanticNodes;
        this.highDegreeNonSignalizedIntersections = highDegreeNonSignalizedIntersections;
        this.movementsWithoutLaneInfo = movementsWithoutLaneInfo;
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public int candidateSignalizedJunctions() {
        return candidateSignalizedJunctions;
    }

    public int confirmedSignalizedJunctions() {
        return confirmedSignalizedJunctions;
    }

    public int preservedSignalNodes() {
        return preservedSignalNodes;
    }

    public int collapsedGeometryNodes() {
        return collapsedGeometryNodes;
    }

    public int preservedSemanticNodes() {
        return preservedSemanticNodes;
    }

    public int highDegreeNonSignalizedIntersections() {
        return highDegreeNonSignalizedIntersections;
    }

    public int movementsWithoutLaneInfo() {
        return movementsWithoutLaneInfo;
    }

    public List<OsmImportIssue> issues() {
        return issues;
    }

    /** Structural readiness: true when there are no ERROR-severity findings. */
    public boolean ok() {
        for (OsmImportIssue i : issues) {
            if (i.severity() == OsmIssueSeverity.ERROR) {
                return false;
            }
        }
        return true;
    }

    public String summary() {
        return "SignalReadiness[junctions=" + candidateSignalizedJunctions
                + ",signalNodes=" + preservedSignalNodes
                + ",collapsed=" + collapsedGeometryNodes
                + ",preserved=" + preservedSemanticNodes
                + ",issues=" + issues.size()
                + (ok() ? ",ok" : ",NOT_OK") + "]";
    }
}
