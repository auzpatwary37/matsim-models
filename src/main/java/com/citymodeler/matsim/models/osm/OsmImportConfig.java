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
        String attributionText) {

    public OsmImportConfig {
        osmFile = Objects.requireNonNull(osmFile, "osmFile");
        targetCrs = Objects.requireNonNull(targetCrs, "targetCrs");
        sourceName = Objects.requireNonNull(sourceName, "sourceName");
        sourceLicense = Objects.requireNonNull(sourceLicense, "sourceLicense");
        attributionText = Objects.requireNonNull(attributionText, "attributionText");
    }

    public static OsmImportConfig of(Path osmFile, String targetCrs) {
        return new OsmImportConfig(
                osmFile,
                targetCrs,
                true,
                true,
                "OpenStreetMap",
                "ODbL-1.0",
                "\u00A9 OpenStreetMap contributors");
    }
}
