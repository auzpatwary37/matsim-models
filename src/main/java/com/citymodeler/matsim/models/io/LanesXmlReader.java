package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;

/**
 * Reads the published MATSim {@code laneDefinitions_v2.0} shape. Feeding a
 * legacy {@code <lanes>/<assignment>} document yields an empty model (the
 * legacy element names are not recognized); that tolerance is intentional and
 * requires no special handling.
 */
public final class LanesXmlReader {
    private static final String SCHEMA = "/schemas/v2/laneDefinitions_v2.0.xsd";
    private final boolean validateSchema;

    public LanesXmlReader() {
        this(false);
    }

    public LanesXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public Lanes read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public Lanes read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public Lanes read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    private Lanes read(Element root) {
        LaneReadResult result = LaneEnvelopeReader.readDocument(root, null, null);
        return result.lanes().orElseGet(Lanes::new);
    }

    /**
     * Reads an explicit lane definitions file and validates its references
     * against {@code network}. Additive 1.0.4 API: unlike {@link #read}, this
     * reports structured outcomes (dialect, state, issues) instead of an
     * empty-model-on-mismatch, and recognizes the canonical v2.0/v1.1
     * envelopes as well as the historic assignment format.
     */
    public LaneReadResult readResult(Path path, com.citymodeler.matsim.models.network.Network network) {
        org.w3c.dom.Document document;
        try {
            document = validateSchema
                    ? XmlSupport.parse(path, SCHEMA)
                    : XmlSupport.parse(path);
        } catch (MatsimParseException exception) {
            throw new BundleReadException("Could not read lanes from " + path, exception,
                    java.util.List.of(new com.citymodeler.matsim.models.validation.ValidationIssue(
                            com.citymodeler.matsim.models.validation.ValidationSeverity.ERROR,
                            "io", "io.read_failed", exception.getMessage(), null, null,
                            com.citymodeler.matsim.models.validation.SourceLocation.of(path, "/"),
                            null, null)));
        }
        return LaneEnvelopeReader.readDocument(document.getDocumentElement(), path, network);
    }

    /**
     * Restores the {@code toLane} ids a mixed lane had to drop from {@code <leadsTo>} (the published
     * {@code xs:choice} cannot carry both branches). The writer joins them into the
     * {@code osm:lane.toLaneIds} attribute; parsing it back makes a mixed-lane relationship
     * reversible through a write/read round-trip rather than degrading to metadata (spec Part 2).
     * Ids already present from an explicit {@code toLane} branch are not duplicated.
     */
    static void rehydrateToLaneIds(Lane lane) {
        Object raw = lane.getAttributes().getAttribute(LanesXmlWriter.TO_LANE_IDS_ATTRIBUTE);
        if (raw == null) {
            return;
        }
        java.util.Set<String> existing = new java.util.LinkedHashSet<>();
        for (Id<Lane> id : lane.getToLaneIds()) {
            existing.add(id.toString());
        }
        for (String token : raw.toString().split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty() || !existing.add(trimmed)) {
                continue;
            }
            lane.addToLaneId(Id.create(trimmed, Lane.class));
        }
    }
}
