package com.citymodeler.matsim.models.osm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.io.MatsimParseException;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationMemberRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmNetworkImporter {

    private final CoordinateTransform transform;

    public OsmNetworkImporter() {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem wgs84 = crsFactory.createFromName("EPSG:4326");
        CoordinateReferenceSystem target = crsFactory.createFromName("EPSG:3857");
        this.transform = new CoordinateTransformFactory().createTransform(wgs84, target);
    }

    public OsmImportResult read(OsmImportConfig config) {
        try {
            OsmImportResult result;
            if (isPbf(config.osmFile())) {
                result = OsmPbfReader.read(config.osmFile(), config);
            } else {
                result = doParse(config);
            }
            if (config.boundary() != null) {
                result = applyBoundaryFilter(result, config.boundary());
            }
            return result;
        } catch (MatsimParseException e) {
            throw e;
        } catch (Exception e) {
            throw new MatsimParseException("Failed to parse OSM file: " + e.getMessage(), e);
        }
    }

    private static OsmImportResult applyBoundaryFilter(OsmImportResult result, OsmBoundary boundary) {
        OsmPolygonFilter filter = new OsmPolygonFilter(boundary);

        var filteredNodes = result.nodes().entrySet().stream()
                .filter(e -> filter.keepNode(e.getValue()))
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, java.util.Map.Entry::getValue,
                        (a, b) -> a, java.util.LinkedHashMap::new));

        var filteredWays = result.ways().entrySet().stream()
                .filter(e -> filter.keepWay(e.getValue(), filteredNodes))
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, java.util.Map.Entry::getValue,
                        (a, b) -> a, java.util.LinkedHashMap::new));

        var keptWayIds = filteredWays.keySet();
        var keptNodeIds = filteredNodes.keySet();
        var filteredRelations = result.relations().entrySet().stream()
                .filter(e -> e.getValue().members().stream().anyMatch(m ->
                        (m.type() == OsmElementType.WAY && keptWayIds.contains(m.ref())) ||
                        (m.type() == OsmElementType.NODE && keptNodeIds.contains(m.ref()))))
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, java.util.Map.Entry::getValue,
                        (a, b) -> a, java.util.LinkedHashMap::new));

        return new OsmImportResult(filteredNodes, filteredWays, filteredRelations,
                result.issues(), result.provenance());
    }

    private static boolean isPbf(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".pbf");
    }

    private OsmImportResult doParse(OsmImportConfig config) throws XMLStreamException, IOException {
        Map<String, OsmNodeRecord> nodes = new LinkedHashMap<>();
        Map<String, OsmWayRecord> ways = new LinkedHashMap<>();
        Map<String, OsmRelationRecord> relations = new LinkedHashMap<>();
        List<OsmImportIssue> issues = new ArrayList<>();

        CoordinateTransform localTransform = createTransform(config.targetCrs());

        try (InputStream stream = openInputStream(config.osmFile())) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(stream);

            boolean inNode = false;
            boolean inWay = false;
            boolean inRelation = false;
            String nodeId = null;
            String wayId = null;
            String relationId = null;
            double nodeLat = 0.0;
            double nodeLon = 0.0;
            Map<String, String> currentTags = new HashMap<>();
            List<String> currentNodeRefs = new ArrayList<>();
            List<OsmRelationMemberRecord> currentMembers = new ArrayList<>();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    switch (name) {
                        case "node" -> {
                            inNode = true;
                            nodeId = reader.getAttributeValue(null, "id");
                            nodeLat = Double.parseDouble(reader.getAttributeValue(null, "lat"));
                            nodeLon = Double.parseDouble(reader.getAttributeValue(null, "lon"));
                            currentTags.clear();
                        }
                        case "way" -> {
                            inWay = true;
                            wayId = reader.getAttributeValue(null, "id");
                            currentTags.clear();
                            currentNodeRefs.clear();
                        }
                        case "relation" -> {
                            inRelation = true;
                            relationId = reader.getAttributeValue(null, "id");
                            currentTags.clear();
                            currentMembers.clear();
                        }
                        case "nd" -> {
                            if (inWay) {
                                currentNodeRefs.add(reader.getAttributeValue(null, "ref"));
                            }
                        }
                        case "member" -> {
                            if (inRelation) {
                                OsmElementType type = OsmElementType.valueOf(
                                        reader.getAttributeValue(null, "type").toUpperCase());
                                String ref = reader.getAttributeValue(null, "ref");
                                String role = reader.getAttributeValue(null, "role");
                                currentMembers.add(new OsmRelationMemberRecord(type, ref, role));
                            }
                        }
                        case "tag" -> {
                            String k = reader.getAttributeValue(null, "k");
                            String v = reader.getAttributeValue(null, "v");
                            if (k != null && v != null) {
                                currentTags.put(k, v);
                            }
                        }
                        default -> { }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    switch (name) {
                        case "node" -> {
                            if (inNode && nodeId != null) {
                                OsmTagSet tagSet = buildTagSet(config, currentTags);
                                Coord projected = project(nodeLon, nodeLat, localTransform, nodeId);
                                nodes.put(nodeId, new OsmNodeRecord(nodeId, nodeLon, nodeLat, projected, tagSet));
                            }
                            inNode = false;
                            nodeId = null;
                        }
                        case "way" -> {
                            if (inWay && wayId != null) {
                                OsmTagSet tagSet = buildTagSet(config, currentTags);
                                ways.put(wayId, new OsmWayRecord(wayId, List.copyOf(currentNodeRefs), tagSet));
                            }
                            inWay = false;
                            wayId = null;
                        }
                        case "relation" -> {
                            if (inRelation && relationId != null) {
                                OsmTagSet tagSet = buildTagSet(config, currentTags);
                                relations.put(relationId,
                                        new OsmRelationRecord(relationId, List.copyOf(currentMembers), tagSet));
                            }
                            inRelation = false;
                            relationId = null;
                        }
                        default -> { }
                    }
                }
            }
        }

        String inputName = config.osmFile().getFileName().toString();
        if (inputName.endsWith(".gz")) {
            inputName = inputName.substring(0, inputName.length() - 3);
        }
        OsmProvenance provenance = new OsmProvenance(
                config.sourceName(),
                config.sourceLicense(),
                config.attributionText(),
                inputName,
                config.targetCrs(),
                "PRESERVE_AS_LINK_GEOMETRY",
                config.keepRawTags(),
                Instant.EPOCH);

        return new OsmImportResult(nodes, ways, relations, List.copyOf(issues), provenance);
    }

    private OsmTagSet buildTagSet(OsmImportConfig config, Map<String, String> parsedTags) {
        return OsmTagSet.of(parsedTags);
    }

    private Coord project(double lon, double lat, CoordinateTransform t, String elementId) {
        ProjCoordinate src = new ProjCoordinate(lon, lat);
        ProjCoordinate dst = new ProjCoordinate();
        if (t.transform(src, dst) == null) {
            throw new MatsimParseException(
                    "Failed to project OSM element " + elementId + " at (" + lon + ", " + lat + ")");
        }
        return new Coord(dst.x, dst.y);
    }

    private CoordinateTransform createTransform(String targetCrs) {
        if ("EPSG:3857".equals(targetCrs)) {
            return transform;
        }
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem wgs84 = crsFactory.createFromName("EPSG:4326");
        CoordinateReferenceSystem target = crsFactory.createFromName(targetCrs);
        return new CoordinateTransformFactory().createTransform(wgs84, target);
    }

    static InputStream openInputStream(Path path) throws IOException {
        InputStream inputStream = Files.newInputStream(path);
        if (path.toString().endsWith(".gz")) {
            try {
                return new GZIPInputStream(inputStream);
            } catch (IOException exception) {
                inputStream.close();
                throw exception;
            }
        }
        return inputStream;
    }
}
