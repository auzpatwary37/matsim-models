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

/**
 * Two-pass StAX parser that reads an OSM {@code .osm} XML file and produces
 * {@link OsmFacility} records for businesses (nodes/ways with business tags)
 * and households (residential building ways).
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
 * <p>Business and household detection (and the deterministic business-tag
 * selection) is delegated to {@link OsmFacilityConfig}; a business wins over a
 * household when both apply. Facility ids are prefixed with {@code "n"} or
 * {@code "w"} so that a colliding OSM node id and way id never clash.
 */
public final class OsmFacilityParser {

    private static final double EARTH_RADIUS_M = 6371000.0;

    private final OsmFacilityConfig config;

    public OsmFacilityParser(OsmFacilityConfig config) {
        this.config = config;
    }

    public List<OsmFacility> parse(Path osmFile) {
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
                    double sumLon = 0, sumLat = 0;
                    int n = 0;
                    for (String ref : way.nodeRefs) {
                        double[] c = nodeCoords.get(ref);
                        if (c != null) {
                            sumLon += c[0];
                            sumLat += c[1];
                            n++;
                        }
                    }
                    if (n == 0) {
                        continue;
                    }
                    double lon = sumLon / n;
                    double lat = sumLat / n;

                    double area = 0.0;
                    int levels = 0;
                    if (config.isHouseholdBuilding(way.tags.get("building"))) {
                        area = polygonAreaM2(way.nodeRefs, nodeCoords);
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
     * tag or a recognized household {@code building} value.
     */
    private boolean qualifies(Map<String, String> tags) {
        return selectBusinessType(tags) != null
            || config.isHouseholdBuilding(tags.get("building"));
    }

    /**
     * Selects the business tag value deterministically by iterating
     * {@code config.businessKeys()} in sorted (natural) order and returning the
     * value of the first key present. Returns {@code null} if no business key is
     * present.
     */
    private String selectBusinessType(Map<String, String> tags) {
        List<String> keys = new ArrayList<>(config.businessKeys());
        Collections.sort(keys);
        for (String key : keys) {
            if (tags.containsKey(key)) {
                return tags.get(key);
            }
        }
        return null;
    }

    private OsmFacility toFacility(String id, double lon, double lat, Map<String, String> tags,
                                   double area, int levels) {
        String type = selectBusinessType(tags);
        boolean isBusiness = type != null;
        boolean isHousehold = !isBusiness && config.isHouseholdBuilding(tags.get("building"));
        if (!isBusiness && !isHousehold) {
            return null;
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
            type,
            tags.get("opening_hours"),
            address,
            area,
            levels,
            isHousehold);
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
     * spherical excess formula. Returns 0 if the ring is degenerate.
     */
    static double polygonAreaM2(List<String> nodeRefs, Map<String, double[]> nodeCoords) {
        List<double[]> ring = new ArrayList<>();
        for (String ref : nodeRefs) {
            double[] c = nodeCoords.get(ref);
            if (c != null) {
                ring.add(c);
            }
        }
        if (ring.size() < 3) {
            return 0.0;
        }
        double area = 0.0;
        int n = ring.size();
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
