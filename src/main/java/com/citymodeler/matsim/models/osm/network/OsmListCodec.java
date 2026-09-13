package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Reversible encoding for OSM provenance lists stored in a single network attribute string. Plain
 * comma joining is ambiguous because OSM names can themselves contain commas, so commas and
 * backslashes are escaped. Values without those characters encode identically to the historic
 * comma-joined form, keeping existing output and readers compatible.
 */
public final class OsmListCodec {

    private OsmListCodec() {
    }

    public static String encode(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            escapeInto(sb, values.get(i));
        }
        return sb.toString();
    }

    public static List<String> decode(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == ',') {
                addIfPresent(out, current);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addIfPresent(out, current);
        return List.copyOf(out);
    }

    private static void escapeInto(StringBuilder sb, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
    }

    private static void addIfPresent(List<String> out, StringBuilder current) {
        String trimmed = current.toString().trim();
        if (!trimmed.isEmpty()) {
            out.add(trimmed);
        }
    }
}
