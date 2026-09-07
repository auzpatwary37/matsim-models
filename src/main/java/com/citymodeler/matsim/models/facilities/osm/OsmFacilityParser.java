package com.citymodeler.matsim.models.facilities.osm;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Streaming parser that reads an OSM {@code .osm} XML file and produces
 * {@link OsmFacility} records for businesses (nodes/ways with business tags)
 * and households (residential building ways).
 *
 * <p>Uses StAX so it can handle multi-gigabyte OSM extracts without loading
 * the whole file into memory.</p>
 */
public final class OsmFacilityParser {

    private static final double EARTH_RADIUS_M = 6371000.0;

    private final OsmFacilityConfig config;

    public OsmFacilityParser(OsmFacilityConfig config) {
        this.config = config;
    }

    public List<OsmFacility> parse(Path osmFile) {
        List<OsmFacility> result = new ArrayList<>();
        Map<String, double[]> nodeCoords = new HashMap<>();
        try (InputStream in = java.nio.file.Files.newInputStream(osmFile)) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("node".equals(name)) {
                        handleNode(reader, nodeCoords, result);
                    } else if ("way".equals(name)) {
                        handleWay(reader, nodeCoords, result);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OSM file: " + osmFile, e);
        }
        return result;
    }

    private void handleNode(XMLStreamReader reader, Map<String, double[]> nodeCoords,
                            List<OsmFacility> result) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        double lat = Double.parseDouble(reader.getAttributeValue(null, "lat"));
        double lon = Double.parseDouble(reader.getAttributeValue(null, "lon"));
        Map<String, String> tags = readTags(reader);
        nodeCoords.put(id, new double[]{lon, lat});

        OsmFacility facility = toFacility(id, lon, lat, tags, 0.0, 0, false);
        if (facility != null) {
            result.add(facility);
        }
    }

    private void handleWay(XMLStreamReader reader, Map<String, double[]> nodeCoords,
                           List<OsmFacility> result) throws XMLStreamException {
        String id = reader.getAttributeValue(null, "id");
        List<String> nodeRefs = new ArrayList<>();
        Map<String, String> tags = new LinkedHashMap<>();
        boolean done = false;
        while (reader.hasNext() && !done) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                if ("nd".equals(name)) {
                    nodeRefs.add(reader.getAttributeValue(null, "ref"));
                } else if ("tag".equals(name)) {
                    tags.put(reader.getAttributeValue(null, "k"),
                             reader.getAttributeValue(null, "v"));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "way".equals(reader.getLocalName())) {
                done = true;
            }
        }

        // centroid of the way's nodes
        double sumLon = 0, sumLat = 0;
        int n = 0;
        for (String ref : nodeRefs) {
            double[] c = nodeCoords.get(ref);
            if (c != null) {
                sumLon += c[0];
                sumLat += c[1];
                n++;
            }
        }
        if (n == 0) {
            return;
        }
        double lon = sumLon / n;
        double lat = sumLat / n;

        double area = 0.0;
        int levels = 0;
        String building = tags.get("building");
        if (building != null && config.isHouseholdBuilding(building)) {
            area = polygonAreaM2(nodeRefs, nodeCoords);
            levels = parseInt(tags.get("building:levels"));
        }

        OsmFacility facility = toFacility(id, lon, lat, tags, area, levels, true);
        if (facility != null) {
            result.add(facility);
        }
    }

    private OsmFacility toFacility(String id, double lon, double lat, Map<String, String> tags,
                                   double area, int levels, boolean isWay) {
        // Determine if business or household
        boolean isBusiness = false;
        String type = null;
        for (String key : config.businessKeys()) {
            if (tags.containsKey(key)) {
                isBusiness = true;
                type = tags.get(key);
                break;
            }
        }
        String building = tags.get("building");
        boolean isHousehold = !isBusiness && building != null && config.isHouseholdBuilding(building);
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

    private Map<String, String> readTags(XMLStreamReader reader) throws XMLStreamException {
        Map<String, String> tags = new LinkedHashMap<>();
        boolean done = false;
        while (reader.hasNext() && !done) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "tag".equals(reader.getLocalName())) {
                tags.put(reader.getAttributeValue(null, "k"),
                         reader.getAttributeValue(null, "v"));
            } else if (event == XMLStreamConstants.END_ELEMENT && "node".equals(reader.getLocalName())) {
                done = true;
            }
        }
        return tags;
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
            double ax = Math.toRadians(a[0]);
            double ay = Math.toRadians(a[1]);
            double bx = Math.toRadians(b[0]);
            double by = Math.toRadians(b[1]);
            area += Math.toRadians(b[0] - a[0]) * (2 + Math.sin(ay) + Math.sin(by));
        }
        area = Math.abs(area * EARTH_RADIUS_M * EARTH_RADIUS_M / 2.0);
        return area;
    }
}
