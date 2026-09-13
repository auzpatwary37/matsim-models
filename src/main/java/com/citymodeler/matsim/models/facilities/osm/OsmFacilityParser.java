package com.citymodeler.matsim.models.facilities.osm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.citymodeler.matsim.models.osm.OsmImportConfig;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmNetworkImporter;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Two-pass StAX parser that reads an OSM {@code .osm} XML file and produces
 * {@link OsmFacility} records for businesses (use-tagged POIs and ways, plus
 * commercial/educational/health buildings) and households (residential
 * buildings).
 *
 * <p>The file is streamed twice. Pass 1 walks every node (emitting qualifying
 * node facilities using the node's own coordinates) and records which node ids
 * belong to qualifying ways. Pass 2 keeps only the coordinates of those needed
 * nodes and emits the qualifying way facilities (centroid + footprint area).
 *
 * <p>Retained memory is therefore bounded by the number of nodes that belong to
 * qualifying ways, not by the total node count, which keeps the parser usable on
 * multi-gigabyte city extracts without holding a global coordinate map.
 *
 * <p>Qualification, the deterministic business-tag selection, and building-type
 * classification are delegated to {@link OsmFacilityConfig}; a use-tag business
 * wins over a building classification when both apply. Way facilities are placed
 * at the area-weighted polygon centroid of their (deduplicated) footprint.
 * Facility ids are prefixed with {@code "n"} or {@code "w"} so that a colliding
 * OSM node id and way id never clash.
 */
public final class OsmFacilityParser {

    private static final double EARTH_RADIUS_M = 6371000.0;

    private final OsmFacilityConfig config;

    public OsmFacilityParser(OsmFacilityConfig config) {
        this.config = config;
    }

    public List<OsmFacility> parse(Path osmFile) {
        if (isPbf(osmFile)) {
            return parsePbf(osmFile);
        }
        List<OsmFacility> result = new ArrayList<>();
        try {
            Set<String> neededNodeIds = new HashSet<>();
            passOne(osmFile, result, neededNodeIds);
            passTwo(osmFile, result, neededNodeIds);
        } catch (XMLStreamException | IOException e) {
            throw new RuntimeException("Failed to parse OSM file: " + osmFile, e);
        }
        return result;
    }

    private static boolean isPbf(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".pbf");
    }

    /**
     * PBF path: reuse the network importer's binary reader (it already decodes nodes, ways, and
     * tags), then apply the SAME facility qualification/centroid logic used for OSM XML. Facility
     * coordinates stay WGS84 lon/lat; the converter projects them into the target CRS.
     */
    private List<OsmFacility> parsePbf(Path pbfFile) {
        OsmImportResult imported = new OsmNetworkImporter()
                .read(OsmImportConfig.of(pbfFile, config.targetCrs()));
        List<OsmFacility> result = new ArrayList<>();

        // Nodes: same qualification as the XML pass-1 node branch.
        for (OsmNodeRecord node : imported.nodes().values()) {
            Map<String, String> tags = node.tags().asMap();
            if (qualifies(tags)) {
                OsmFacility facility = toFacility(
                        "n" + node.id(), node.lon(), node.lat(), tags, 0.0, 0);
                if (facility != null) {
                    result.add(facility);
                }
            }
        }

        // Ways: same qualification + centroid logic as the XML pass-2 way branch.
        for (OsmWayRecord way : imported.ways().values()) {
            Map<String, String> tags = way.tags().asMap();
            if (!qualifies(tags)) {
                continue;
            }
            List<double[]> ring = new ArrayList<>();
            List<String> refs = way.nodeRefs();
            if (refs.size() >= 2 && refs.get(0).equals(refs.get(refs.size() - 1))) {
                refs = refs.subList(0, refs.size() - 1);
            }
            for (String ref : refs) {
                OsmNodeRecord n = imported.nodes().get(ref);
                if (n != null) {
                    ring.add(new double[]{n.lon(), n.lat()});
                }
            }
            if (ring.isEmpty()) {
                continue;
            }
            double[] centroid = polygonCentroid(ring);
            double area = 0.0;
            int levels = 0;
            if (config.isResidentialBuilding(tags.get("building"))) {
                area = polygonAreaM2(ring);
                levels = parseInt(tags.get("building:levels"));
            }
            OsmFacility facility = toFacility(
                    "w" + way.id(), centroid[0], centroid[1], tags, area, levels);
            if (facility != null) {
                result.add(facility);
            }
        }
        return result;
    }

    /**
     * Pass 1: emit qualifying node facilities and collect the node ids that
     * qualify for pass 2 (those referenced by qualifying ways).
     */
    private void passOne(Path osmFile, List<OsmFacility> result,
                         Set<String> neededNodeIds) throws XMLStreamException, IOException {
        try (InputStream in = Files.newInputStream(osmFile)) {
            XMLStreamReader reader = newHardenedFactory().createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String name = reader.getLocalName();
                if ("node".equals(name)) {
                    NodeData node = readNode(reader);
                    if (qualifies(node.tags)) {
                        OsmFacility facility = toFacility(
                            "n" + node.id, node.lon, node.lat, node.tags, 0.0, 0);
                        if (facility != null) {
                            result.add(facility);
                        }
                    }
                } else if ("way".equals(name)) {
                    WayData way = readWay(reader);
                    if (qualifies(way.tags)) {
                        neededNodeIds.addAll(way.nodeRefs);
                    }
                }
            }
            reader.close();
        }
    }

    /**
     * Pass 2: keep the coordinates of the needed nodes, then emit qualifying
     * way facilities resolved against them. OSM files emit all nodes before any
     * ways, so the coordinate map is fully populated before a way is reached.
     */
    private void passTwo(Path osmFile, List<OsmFacility> result,
                         Set<String> neededNodeIds) throws XMLStreamException, IOException {
        Map<String, double[]> nodeCoords = new HashMap<>();
        try (InputStream in = Files.newInputStream(osmFile)) {
            XMLStreamReader reader = newHardenedFactory().createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String name = reader.getLocalName();
                if ("node".equals(name)) {
                    String id = reader.getAttributeValue(null, "id");
                    double lat = Double.parseDouble(reader.getAttributeValue(null, "lat"));
                    double lon = Double.parseDouble(reader.getAttributeValue(null, "lon"));
                    skipChildren(reader, "node", null);
                    if (neededNodeIds.contains(id)) {
                        nodeCoords.put(id, new double[]{lon, lat});
                    }
                } else if ("way".equals(name)) {
                    WayData way = readWay(reader);
                    if (!qualifies(way.tags)) {
                        continue;
                    }
                    List<double[]> ring = resolveRing(way.nodeRefs, nodeCoords);
                    if (ring.isEmpty()) {
                        continue;
                    }
                    double[] centroid = polygonCentroid(ring);
                    double lon = centroid[0];
                    double lat = centroid[1];

                    double area = 0.0;
                    int levels = 0;
                    if (config.isResidentialBuilding(way.tags.get("building"))) {
                        area = polygonAreaM2(ring);
                        levels = parseInt(way.tags.get("building:levels"));
                    }

                    OsmFacility facility = toFacility(
                        "w" + way.id, lon, lat, way.tags, area, levels);
                    if (facility != null) {
                        result.add(facility);
                    }
                }
            }
            reader.close();
        }
    }

    /**
     * Builds an {@link XMLInputFactory} with DTD processing and external
     * entities disabled, matching the library's defensive XML defaults.
     */
    private static XMLInputFactory newHardenedFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    /**
     * Reads a {@code <node>} element that is already positioned on its start tag,
     * advancing the reader past its closing tag.
     */
    private NodeData readNode(XMLStreamReader reader) throws XMLStreamException {
        NodeData node = new NodeData();
        node.id = reader.getAttributeValue(null, "id");
        node.lat = Double.parseDouble(reader.getAttributeValue(null, "lat"));
        node.lon = Double.parseDouble(reader.getAttributeValue(null, "lon"));
        skipChildren(reader, "node", node.tags);
        return node;
    }

    /**
     * Reads a {@code <way>} element that is already positioned on its start tag,
     * advancing the reader past its closing tag.
     */
    private WayData readWay(XMLStreamReader reader) throws XMLStreamException {
        WayData way = new WayData();
        way.id = reader.getAttributeValue(null, "id");
        boolean done = false;
        while (reader.hasNext() && !done) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                if ("nd".equals(name)) {
                    way.nodeRefs.add(reader.getAttributeValue(null, "ref"));
                } else if ("tag".equals(name)) {
                    way.tags.put(reader.getAttributeValue(null, "k"),
                                 reader.getAttributeValue(null, "v"));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "way".equals(reader.getLocalName())) {
                done = true;
            }
        }
        return way;
    }

    /**
     * Advances the reader past the remaining children of an element that is
     * already positioned on its start tag. If a non-null {@code tags} map is
     * supplied, any {@code <tag>} children encountered are collected.
     */
    private void skipChildren(XMLStreamReader reader, String elementName,
                              Map<String, String> tags) throws XMLStreamException {
        boolean done = false;
        while (reader.hasNext() && !done) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT
                    && "tag".equals(reader.getLocalName()) && tags != null) {
                tags.put(reader.getAttributeValue(null, "k"),
                         reader.getAttributeValue(null, "v"));
            } else if (event == XMLStreamConstants.END_ELEMENT && elementName.equals(reader.getLocalName())) {
                done = true;
            }
        }
    }

    /**
     * A node or way element qualifies as a facility if it carries any business
     * tag or a qualifying {@code building} value (residential, commercial,
     * educational, or health).
     */
    private boolean qualifies(Map<String, String> tags) {
        return selectBusinessKey(tags) != null
            || config.isQualifyingBuilding(tags.get("building"));
    }

    /**
     * Selects the business tag key deterministically by iterating
     * {@code config.businessKeys()} in sorted (natural) order and returning the
     * first key present whose value is not an excluded (transit-infrastructure)
     * amenity. Returns {@code null} if no qualifying business key is present.
     */
    private String selectBusinessKey(Map<String, String> tags) {
        List<String> keys = new ArrayList<>(config.businessKeys());
        Collections.sort(keys);
        for (String key : keys) {
            if (tags.containsKey(key) && !config.isExcludedFacility(key, tags.get(key))) {
                return key;
            }
        }
        return null;
    }

    /**
     * Classifies an element into its {@code (osmKey, osmValue, isHousehold)}
     * triple. A use-tag business takes precedence; otherwise a qualifying
     * {@code building} value is used (residential values become households).
     * Returns {@code null} if the element is not a facility.
     */
    private OsmFacility toFacility(String id, double lon, double lat, Map<String, String> tags,
                                   double area, int levels) {
        String businessKey = selectBusinessKey(tags);
        String osmKey;
        String osmValue;
        boolean isHousehold;
        if (businessKey != null) {
            osmKey = businessKey;
            osmValue = tags.get(businessKey);
            isHousehold = false;
        } else {
            String building = tags.get("building");
            if (!config.isQualifyingBuilding(building)) {
                return null;
            }
            osmKey = "building";
            osmValue = building;
            isHousehold = config.isResidentialBuilding(building);
        }

        Map<String, String> address = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : tags.entrySet()) {
            if (e.getKey().startsWith("addr:")) {
                address.put(e.getKey(), e.getValue());
            }
        }

        return new OsmFacility(
            id, lon, lat,
            tags.get("name"),
            osmKey,
            osmValue,
            tags.get("opening_hours"),
            address,
            area,
            levels,
            isHousehold);
    }

    /**
     * Resolves a way's node refs to their coordinates, dropping the repeated
     * closing node of a closed polygon (OSM repeats the first ref as the last).
     */
    private List<double[]> resolveRing(List<String> nodeRefs, Map<String, double[]> nodeCoords) {
        List<String> refs = nodeRefs;
        if (refs.size() >= 2 && refs.get(0).equals(refs.get(refs.size() - 1))) {
            refs = refs.subList(0, refs.size() - 1);
        }
        List<double[]> ring = new ArrayList<>();
        for (String ref : refs) {
            double[] c = nodeCoords.get(ref);
            if (c != null) {
                ring.add(c);
            }
        }
        return ring;
    }

    private static int parseInt(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Approximate area (m²) of a closed ring of lon/lat points using the
     * spherical excess formula. The ring must be the deduplicated polygon outline.
     * Returns 0 if the ring is degenerate.
     */
    static double polygonAreaM2(List<double[]> ring) {
        int n = ring.size();
        if (n < 3) {
            return 0.0;
        }
        double area = 0.0;
        for (int i = 0; i < n; i++) {
            double[] a = ring.get(i);
            double[] b = ring.get((i + 1) % n);
            double ay = Math.toRadians(a[1]);
            double by = Math.toRadians(b[1]);
            area += Math.toRadians(b[0] - a[0]) * (2 + Math.sin(ay) + Math.sin(by));
        }
        area = Math.abs(area * EARTH_RADIUS_M * EARTH_RADIUS_M / 2.0);
        return area;
    }

    /**
     * Computes the area-weighted (shoelace) centroid of a polygon ring of lon/lat
     * points, which represents a building footprint far better than the plain
     * vertex average (which shifts toward the dense side of an irregular polygon
     * and can even fall outside it).
     *
     * <p>The centroid is evaluated in a local equirectangular frame centred on the
     * ring's mean — an excellent planar approximation for building-sized polygons —
     * then converted back to lon/lat so target-CRS reprojection stays the
     * converter's responsibility.
     *
     * <p>If the ring is degenerate (fewer than three points, or ~zero area such as an
     * open way or a collapsed footprint) it falls back to the vertex average.
     *
     * @return a {@code {lon, lat}} array, or {@code null} for an empty ring
     */
    static double[] polygonCentroid(List<double[]> ring) {
        int n = ring.size();
        if (n == 0) {
            return null;
        }
        double lon0 = 0, lat0 = 0;
        for (double[] c : ring) {
            lon0 += c[0];
            lat0 += c[1];
        }
        lon0 /= n;
        lat0 /= n;
        if (n < 3) {
            return new double[]{lon0, lat0};
        }
        double cosLat = Math.cos(Math.toRadians(lat0));
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (ring.get(i)[0] - lon0) * cosLat * EARTH_RADIUS_M;
            ys[i] = (ring.get(i)[1] - lat0) * EARTH_RADIUS_M;
        }
        double area2 = 0, cxNum = 0, cyNum = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double cross = xs[i] * ys[j] - xs[j] * ys[i];
            area2 += cross;
            cxNum += (xs[i] + xs[j]) * cross;
            cyNum += (ys[i] + ys[j]) * cross;
        }
        double area = area2 / 2.0;
        if (Math.abs(area) < 1e-6) {
            return new double[]{lon0, lat0};
        }
        double cx = cxNum / (6.0 * area);
        double cy = cyNum / (6.0 * area);
        double clon = lon0 + cx / (cosLat * EARTH_RADIUS_M);
        double clat = lat0 + cy / EARTH_RADIUS_M;
        return new double[]{clon, clat};
    }

    private static final class NodeData {
        String id;
        double lat;
        double lon;
        final Map<String, String> tags = new LinkedHashMap<>();
    }

    private static final class WayData {
        String id;
        final List<String> nodeRefs = new ArrayList<>();
        final Map<String, String> tags = new LinkedHashMap<>();
    }
}
