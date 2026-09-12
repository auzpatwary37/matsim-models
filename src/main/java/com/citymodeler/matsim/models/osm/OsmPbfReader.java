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

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

import crosby.binary.BinaryParser;
import crosby.binary.Osmformat;
import crosby.binary.file.BlockInputStream;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.io.MatsimParseException;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationMemberRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

final class OsmPbfReader {

    private OsmPbfReader() {
    }

    static OsmImportResult read(Path file, OsmImportConfig config) throws IOException {
        CoordinateTransform transform = createTransform(config.targetCrs());

        Collector collector = new Collector(transform);
        try (InputStream in = Files.newInputStream(file);
             BlockInputStream bin = new BlockInputStream(in, collector)) {
            bin.process();
        }

        OsmProvenance provenance = new OsmProvenance(
                config.sourceName(),
                config.sourceLicense(),
                config.attributionText(),
                file.getFileName().toString(),
                config.targetCrs(),
                "PRESERVE_AS_LINK_GEOMETRY",
                config.keepRawTags(),
                Instant.now());

        return new OsmImportResult(collector.nodes, collector.ways, collector.relations,
                List.copyOf(collector.issues), provenance);
    }

    /**
     * Returns a diagnostic when the DenseNodes parallel arrays disagree in length, or {@code null}
     * when they are structurally valid. Malformed/truncated input is reported, not thrown.
     */
    static String denseArrayMismatch(int ids, int lats, int lons) {
        if (lats == ids && lons == ids) {
            return null;
        }
        return "DenseNodes parallel arrays differ in length: ids=" + ids
                + " lats=" + lats + " lons=" + lons + "; skipping block";
    }

    /**
     * Consume the packed {@code keys_vals} stream for one dense node from {@code kvIdx}. The stream
     * is {@code (keyId, valueId)* 0}; a lone key with no following value means the stream is
     * truncated, which is appended to {@code truncation} and stops consumption deterministically.
     * Returns the index just past the consumed tags.
     */
    static int parseDenseTags(List<Integer> keysVals, int kvIdx, Map<String, String> tags,
                              java.util.function.IntFunction<String> stringById,
                              List<String> truncation) {
        while (kvIdx < keysVals.size()) {
            int key = keysVals.get(kvIdx++);
            if (key == 0) {
                break; // end of this node's tags: ((keyId valueId) 0)*
            }
            if (kvIdx >= keysVals.size()) {
                truncation.add("DenseNodes keys_vals ended after key " + key
                        + " without its value; tags dropped");
                break;
            }
            int val = keysVals.get(kvIdx++);
            tags.put(stringById.apply(key), stringById.apply(val));
        }
        return kvIdx;
    }

    private static final class Collector extends BinaryParser {

        final Map<String, OsmNodeRecord> nodes = new LinkedHashMap<>();
        final Map<String, OsmWayRecord> ways = new LinkedHashMap<>();
        final Map<String, OsmRelationRecord> relations = new LinkedHashMap<>();
        final List<OsmImportIssue> issues = new ArrayList<>();
        private final CoordinateTransform transform;

        Collector(CoordinateTransform transform) {
            this.transform = transform;
        }

        @Override
        protected void parse(Osmformat.HeaderBlock header) {
        }

        @Override
        public void complete() {
        }

        @Override
        protected void parseDense(Osmformat.DenseNodes dense) {
            List<Long> ids = dense.getIdList();
            List<Long> lats = dense.getLatList();
            List<Long> lons = dense.getLonList();
            List<Integer> keysVals = dense.getKeysValsList();

            // Valid PBF guarantees these parallel arrays have identical length; a mismatch means a
            // malformed/truncated block. Report it explicitly instead of throwing mid-iteration.
            String mismatch = denseArrayMismatch(ids.size(), lats.size(), lons.size());
            if (mismatch != null) {
                issues.add(new OsmImportIssue(
                        OsmIssueSeverity.WARNING, "pbf-malformed-dense-block", mismatch, null));
                return;
            }

            long idAccum = 0, latAccum = 0, lonAccum = 0;
            int kvIdx = 0;
            for (int i = 0; i < ids.size(); i++) {
                // Dense node ids are delta-encoded within the block, like lat/lon.
                idAccum += ids.get(i);
                long id = idAccum;
                latAccum += lats.get(i);
                lonAccum += lons.get(i);
                double lat = parseLat(latAccum);
                double lon = parseLon(lonAccum);

                if (Math.abs(lat) > 90.0 || Math.abs(lon) > 180.0) {
                    continue;
                }

                try {
                    Map<String, String> tags = new HashMap<>();
                    List<String> truncation = new ArrayList<>();
                    kvIdx = parseDenseTags(keysVals, kvIdx, tags, this::getStringById, truncation);
                    for (String t : truncation) {
                        issues.add(new OsmImportIssue(
                                OsmIssueSeverity.WARNING, "pbf-malformed-tag-stream",
                                t + " (node id " + id + ")", null));
                    }

                    Coord projected = project(lon, lat, String.valueOf(id));
                    nodes.put(String.valueOf(id), new OsmNodeRecord(
                            String.valueOf(id), lon, lat, projected, OsmTagSet.of(tags)));
                } catch (Exception e) {
                    issues.add(new OsmImportIssue(
                            OsmIssueSeverity.WARNING,
                            "pbf-parse-error",
                            "Failed to parse dense node " + id + ": " + e.getMessage(),
                            null));
                }
            }
        }

        /**
         * Returns a diagnostic when the DenseNodes parallel arrays disagree in length, or
         * {@code null} when they are structurally valid.
         */
        static String denseArrayMismatch(int ids, int lats, int lons) {
            return OsmPbfReader.denseArrayMismatch(ids, lats, lons);
        }

        /**
         * Consume the packed {@code keys_vals} stream for one dense node from {@code kvIdx}. The
         * stream is {@code (keyId, valueId)* 0}; a lone key with no following value means the stream
         * is truncated, which is appended to {@code truncation} and stops consumption
         * deterministically. Returns the index just past the consumed tags.
         */
        static int parseDenseTags(List<Integer> keysVals, int kvIdx, Map<String, String> tags,
                                  java.util.function.IntFunction<String> stringById,
                                  List<String> truncation) {
            return OsmPbfReader.parseDenseTags(keysVals, kvIdx, tags, stringById, truncation);
        }

        @Override
        protected void parseNodes(List<Osmformat.Node> nodeList) {
            for (Osmformat.Node node : nodeList) {
                double lat = parseLat(node.getLat());
                double lon = parseLon(node.getLon());
                if (Math.abs(lat) > 90.0 || Math.abs(lon) > 180.0) {
                    continue;
                }

                Map<String, String> tags = new HashMap<>();
                List<Integer> keys = node.getKeysList();
                List<Integer> vals = node.getValsList();
                for (int i = 0; i < keys.size(); i++) {
                    tags.put(getStringById(keys.get(i)), getStringById(vals.get(i)));
                }

                Coord projected;
                try {
                    projected = project(lon, lat, String.valueOf(node.getId()));
                } catch (RuntimeException e) {
                    continue;
                }
                nodes.put(String.valueOf(node.getId()), new OsmNodeRecord(
                        String.valueOf(node.getId()), lon, lat, projected, OsmTagSet.of(tags)));
            }
        }

        @Override
        protected void parseWays(List<Osmformat.Way> wayList) {
            for (Osmformat.Way way : wayList) {
                List<Long> refDeltas = way.getRefsList();
                List<String> nodeRefs = new ArrayList<>(refDeltas.size());
                long lastRef = 0;
                for (long delta : refDeltas) {
                    lastRef += delta;
                    nodeRefs.add(String.valueOf(lastRef));
                }

                Map<String, String> tags = new HashMap<>();
                List<Integer> keys = way.getKeysList();
                List<Integer> vals = way.getValsList();
                for (int i = 0; i < keys.size(); i++) {
                    tags.put(getStringById(keys.get(i)), getStringById(vals.get(i)));
                }

                ways.put(String.valueOf(way.getId()),
                        new OsmWayRecord(String.valueOf(way.getId()), List.copyOf(nodeRefs), OsmTagSet.of(tags)));
            }
        }

        @Override
        protected void parseRelations(List<Osmformat.Relation> relList) {
            for (Osmformat.Relation rel : relList) {
                List<Long> refDeltas = rel.getMemidsList();
                List<OsmRelationMemberRecord> members = new ArrayList<>(refDeltas.size());
                long lastRef = 0;
                for (int i = 0; i < refDeltas.size(); i++) {
                    lastRef += refDeltas.get(i);
                    OsmElementType type = switch (rel.getTypesList().get(i)) {
                        case NODE -> OsmElementType.NODE;
                        case WAY -> OsmElementType.WAY;
                        default -> OsmElementType.RELATION;
                    };
                    String role = getStringById(rel.getRolesSidList().get(i));
                    members.add(new OsmRelationMemberRecord(type, String.valueOf(lastRef), role));
                }

                Map<String, String> tags = new HashMap<>();
                List<Integer> keys = rel.getKeysList();
                List<Integer> vals = rel.getValsList();
                for (int i = 0; i < keys.size(); i++) {
                    tags.put(getStringById(keys.get(i)), getStringById(vals.get(i)));
                }

                relations.put(String.valueOf(rel.getId()),
                        new OsmRelationRecord(String.valueOf(rel.getId()), List.copyOf(members), OsmTagSet.of(tags)));
            }
        }

        private Coord project(double lon, double lat, String elementId) {
            ProjCoordinate src = new ProjCoordinate(lon, lat);
            ProjCoordinate dst = new ProjCoordinate();
            if (transform.transform(src, dst) == null) {
                throw new MatsimParseException(
                        "Failed to project PBF element " + elementId + " at (" + lon + ", " + lat + ")");
            }
            return new Coord(dst.x, dst.y);
        }
    }

    private static CoordinateTransform createTransform(String targetCrs) {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem wgs84 = crsFactory.createFromName("EPSG:4326");
        CoordinateReferenceSystem target = crsFactory.createFromName(targetCrs);
        return new CoordinateTransformFactory().createTransform(wgs84, target);
    }
}
