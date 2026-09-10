package com.citymodeler.matsim.models.osm;

import java.nio.file.Path;
import java.util.Objects;

public record OsmImportConfig(
        Path osmFile,
        String targetCrs,
        boolean keepRawTags,
        boolean writeProvenanceAttributes,
        String sourceName,
        String sourceLicense,
        String attributionText,
        OsmBoundary boundary) {

    public OsmImportConfig {
        osmFile = Objects.requireNonNull(osmFile, "osmFile");
        targetCrs = Objects.requireNonNull(targetCrs, "targetCrs");
        sourceName = Objects.requireNonNull(sourceName, "sourceName");
        sourceLicense = Objects.requireNonNull(sourceLicense, "sourceLicense");
        attributionText = Objects.requireNonNull(attributionText, "attributionText");
    }

    public OsmImportConfig(Path osmFile, String targetCrs, boolean keepRawTags,
                           boolean writeProvenanceAttributes, String sourceName,
                           String sourceLicense, String attributionText) {
        this(osmFile, targetCrs, keepRawTags, writeProvenanceAttributes,
                sourceName, sourceLicense, attributionText, null);
    }

    public static OsmImportConfig of(Path osmFile, String targetCrs) {
        return new OsmImportConfig(
                osmFile,
                targetCrs,
                true,
                true,
                "OpenStreetMap",
                "ODbL-1.0",
                "\u00A9 OpenStreetMap contributors",
                null);
    }

    public static OsmImportConfig of(Path osmFile, String targetCrs, OsmBoundary boundary) {
        return new OsmImportConfig(
                osmFile,
                targetCrs,
                true,
                true,
                "OpenStreetMap",
                "ODbL-1.0",
                "\u00A9 OpenStreetMap contributors",
                boundary);
    }
}
