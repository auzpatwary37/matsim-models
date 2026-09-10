package com.citymodeler.matsim.models.osm;

import java.util.Objects;

public record OsmImportIssue(OsmIssueSeverity severity, String code, String message, OsmElementId elementId) {
    public OsmImportIssue {
        severity = Objects.requireNonNull(severity, "severity");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
    }
}
