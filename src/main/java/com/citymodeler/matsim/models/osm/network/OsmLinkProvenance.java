package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.network.Link;

/**
 * Typed accessors for the provenance attributes written onto a contracted network link by
 * {@link OsmTopologyBuilder}: the full source polyline, the ordered source OSM node ids, and the
 * source OSM way ids/names. These survive a network XML round-trip as ordinary link attributes.
 */
public final class OsmLinkProvenance {

    private OsmLinkProvenance() {
    }

    /** Decoded geometry, node ids, way ids and names read back from a link's attributes. */
    public record Decoded(
            List<Coord> geometry,
            List<String> sourceNodeIds,
            List<String> sourceWayIds,
            List<String> sourceNames) {
    }

    /** Reads the provenance attributes from a link, tolerating their absence (pre-provenance links). */
    public static Decoded of(Link link) {
        return new Decoded(
                parseWktGeometry(stringAttribute(link, "osm:geometry")),
                splitCsv(stringAttribute(link, "osm:sourceNodes")),
                splitCsv(stringAttribute(link, "osm:sourceWays")),
                splitCsv(stringAttribute(link, "osm:sourceNames")));
    }

    /** The representative road name if present. */
    public static Optional<String> name(Link link) {
        String name = stringAttribute(link, "osm:name");
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    private static String stringAttribute(Link link, String key) {
        Object value = link.getAttributes().getAttribute(key);
        return value == null ? null : value.toString();
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return List.copyOf(out);
    }

    /** Parses a WKT {@code LINESTRING (x y, x y, ...)} into coordinates; empty when absent/unparseable. */
    static List<Coord> parseWktGeometry(String wkt) {
        if (wkt == null) {
            return List.of();
        }
        String trimmed = wkt.trim();
        int open = trimmed.indexOf('(');
        int close = trimmed.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return List.of();
        }
        String body = trimmed.substring(open + 1, close);
        List<Coord> points = new ArrayList<>();
        for (String pair : body.split(",")) {
            String[] xy = pair.trim().split("\\s+");
            if (xy.length != 2) {
                return List.of();
            }
            try {
                points.add(new Coord(Double.parseDouble(xy[0]), Double.parseDouble(xy[1])));
            } catch (NumberFormatException e) {
                return List.of();
            }
        }
        return List.copyOf(points);
    }
}
