package com.citymodeler.matsim.models.osm;

import java.time.Instant;
import java.util.Objects;

public record OsmProvenance(
        String sourceName,
        String sourceLicense,
        String attributionText,
        String inputName,
        String targetCrs,
        String geometryMode,
        boolean rawTagsKept,
        Instant importedAt) {

    public OsmProvenance {
        sourceName = Objects.requireNonNull(sourceName, "sourceName");
        sourceLicense = Objects.requireNonNull(sourceLicense, "sourceLicense");
        attributionText = Objects.requireNonNull(attributionText, "attributionText");
        inputName = Objects.requireNonNull(inputName, "inputName");
        targetCrs = Objects.requireNonNull(targetCrs, "targetCrs");
        geometryMode = Objects.requireNonNull(geometryMode, "geometryMode");
        importedAt = Objects.requireNonNull(importedAt, "importedAt");
    }

    public static OsmProvenance defaultFor(String inputName, String targetCrs) {
        return new OsmProvenance(
                "OpenStreetMap",
                "ODbL-1.0",
                "\u00A9 OpenStreetMap contributors",
                inputName,
                targetCrs,
                "PRESERVE_AS_LINK_GEOMETRY",
                true,
                Instant.EPOCH);
    }
}
