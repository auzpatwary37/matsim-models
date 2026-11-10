package com.citymodeler.matsim.models.validation;

import java.nio.file.Path;

/**
 * Physical origin of an issue inside a source artifact.
 *
 * @param source  file the issue was read from; {@code null} for content read
 *                from a string or stream with no backing file
 * @param xmlPath structural path with 1-based occurrence indices, e.g.
 *                {@code /network/lanes[1]/laneDefinitions[1]/lane[3]}
 *                (indices keep duplicates distinguishable)
 * @param line    1-based line, or -1 when the parser does not expose positions
 *                (never fabricated after DTD stripping)
 * @param column  1-based column, or -1 when unavailable
 */
public record SourceLocation(Path source, String xmlPath, int line, int column) {

    public SourceLocation {
        java.util.Objects.requireNonNull(xmlPath, "xmlPath");
    }

    public static SourceLocation of(Path source, String xmlPath) {
        return new SourceLocation(source, xmlPath, -1, -1);
    }
}
