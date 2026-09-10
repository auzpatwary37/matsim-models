package com.citymodeler.matsim.models.gtfs;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Derives and sanitizes GTFS feed IDs. IDs are always `[A-Za-z0-9_-]+`.
 */
public final class GtfsFeedIdCodec {

    private GtfsFeedIdCodec() {
    }

    public static String derive(Path path) {
        String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        // Strip .zip / .gtfs extension
        name = name.replaceAll("(?i)\\.(zip|gtfs)$", "");
        return sanitize(name);
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "feed";
        String cleaned = raw.replaceAll("[^A-Za-z0-9_\\-.]", "_")
                .replaceAll("\\.+", ".")
                .replaceAll("(^_|_$)", "")
                .replaceAll("__+", "_");
        return cleaned.isEmpty() ? "feed" : cleaned;
    }

    /** Assign unique IDs from a set of raw candidates, suffixing -2, -3 on collision. */
    public static Set<String> assignUnique(Set<String> candidates) {
        Set<String> assigned = new java.util.LinkedHashSet<>();
        Set<String> used = new HashSet<>();
        for (String candidate : candidates) {
            String base = sanitize(candidate);
            String id = base;
            int suffix = 2;
            while (used.contains(id)) {
                id = base + "-" + suffix++;
            }
            used.add(id);
            assigned.add(id);
        }
        return assigned;
    }
}
